/**
 * Generic flat-surface viewport. Knows nothing about layouts: items are children flagged
 * with `data-item` and positioned from `data-x`, `data-y`, `data-w`, `data-h`.
 *
 * The server decides where items are and where the viewport goes; this element only
 * applies transforms, handles pointer gestures and reports where the viewport ended up.
 */

type View = { tx: number; ty: number; scale: number };

const STYLE_ID = 'workspace-viewport-style';

function ensureStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
        workspace-viewport { --ws-duration: 400ms; --ws-easing: cubic-bezier(0.4, 0, 0.2, 1); }
        workspace-viewport [data-item] {
            position: absolute; left: 0; top: 0; box-sizing: border-box; overflow: hidden;
            transform-origin: 50% 50%;
            transform: translate(var(--ws-x, 0px), var(--ws-y, 0px)) scale(var(--ws-scale, 1));
        }
        workspace-viewport [data-item][data-animate] {
            transition: transform var(--ws-duration) var(--ws-easing), opacity var(--ws-duration) var(--ws-easing);
        }
        @media (prefers-reduced-motion: reduce) {
            workspace-viewport { --ws-duration: 0ms; }
        }
    `;
    document.head.appendChild(style);
}

class WorkspaceViewport extends HTMLElement {
    private canvas!: HTMLDivElement;
    private tx = 0;
    private ty = 0;
    private scale = 1.0;
    private isDragging = false;
    private dragged = false;
    private touches: Touch[] = [];
    private lastPinchDist = 0;
    private childObserver?: MutationObserver;
    private pendingDone?: () => void;
    private pendingOnEnd?: (e?: Event) => void;
    private settleTimer?: number;
    private sizedQueue: (() => void)[] = [];
    private sized = false;
    /** Items placed this frame; their first coordinates are applied without a transition. */
    private fresh = new Set<HTMLElement>();

    // ── Properties (set from the server) ─────────────────────────────────

    minScale = 0.05;
    maxScale = 4.0;
    fitPadding = 40;
    draggingEnabled = true;
    zoomingEnabled = true;
    initialFitAll = false;
    initialView?: { cx: number; cy: number; scale: number };
    initialFit?: { x: number; y: number; w: number; h: number };

    private _transitionDuration = 400;
    get transitionDuration() { return this._transitionDuration; }
    set transitionDuration(ms: number) {
        this._transitionDuration = ms;
        this.style.setProperty('--ws-duration', `${ms}ms`);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    connectedCallback() {
        ensureStyle();
        this.style.display = 'block';
        this.style.position = 'relative';
        this.style.overflow = 'hidden';
        if (!this.hasAttribute('tabindex')) this.tabIndex = 0;

        if (!this.shadowRoot) {
            const root = this.attachShadow({ mode: 'open' });
            const style = document.createElement('style');
            style.textContent = `
                :host { display: block; position: relative; overflow: hidden; }
                .ws-canvas { position: absolute; left: 0; top: 0; transform-origin: 0 0; will-change: transform; }
            `;
            this.canvas = document.createElement('div');
            this.canvas.className = 'ws-canvas';
            // Layers (slot="below" / slot="above") sit under or over the items, inside the
            // same transformed canvas, so they pan and zoom with the surface.
            for (const name of ['below', '', 'above']) {
                const slot = document.createElement('slot');
                if (name) slot.name = name;
                this.canvas.appendChild(slot);
            }
            root.appendChild(style);
            root.appendChild(this.canvas);
        }
        this.applyTransform();

        // Items stay light-DOM children (so the server can add and remove them); the slot
        // places them inside the transformed canvas.
        this.items().forEach((el) => this.positionItem(el));

        this.childObserver = new MutationObserver((mutations) => {
            for (const m of mutations) {
                if (m.type === 'childList') {
                    m.addedNodes.forEach((node) => {
                        if (node.nodeType === Node.ELEMENT_NODE) this.positionItem(node as HTMLElement);
                    });
                } else if (m.type === 'attributes') {
                    this.positionItem(m.target as HTMLElement);
                }
            }
        });
        this.childObserver.observe(this, {
            childList: true, attributes: true, subtree: true,
            attributeFilter: ['data-x', 'data-y', 'data-w', 'data-h'],
        });

        this.addEventListener('mousedown', this.onMouseDown);
        this.addEventListener('mousemove', this.onMouseMove);
        this.addEventListener('mouseup', this.onMouseUp);
        this.addEventListener('mouseleave', this.onMouseUp);
        this.addEventListener('wheel', this.onWheel, { passive: false });
        this.addEventListener('touchstart', this.onTouchStart, { passive: false });
        this.addEventListener('touchmove', this.onTouchMove, { passive: false });
        this.addEventListener('touchend', this.onTouchEnd, { passive: false });

        const ro = new ResizeObserver(() => {
            if (this.clientWidth > 0 && !this.sized) {
                this.sized = true;
                ro.disconnect();
                if (this.initialFitAll) this.fitAll();
                else if (this.initialView) this.setView(this.initialView.cx, this.initialView.cy, this.initialView.scale);
                else if (this.initialFit) this.fitRect(this.initialFit.x, this.initialFit.y, this.initialFit.w, this.initialFit.h);
                const q = this.sizedQueue;
                this.sizedQueue = [];
                q.forEach((fn) => fn());
            }
        });
        ro.observe(this);
    }

    disconnectedCallback() {
        this.childObserver?.disconnect();
        this.removeEventListener('mousedown', this.onMouseDown);
        this.removeEventListener('mousemove', this.onMouseMove);
        this.removeEventListener('mouseup', this.onMouseUp);
        this.removeEventListener('mouseleave', this.onMouseUp);
        this.removeEventListener('wheel', this.onWheel);
        this.removeEventListener('touchstart', this.onTouchStart);
        this.removeEventListener('touchmove', this.onTouchMove);
        this.removeEventListener('touchend', this.onTouchEnd);
    }

    private whenSized(fn: () => void) {
        if (this.sized) fn();
        else this.sizedQueue.push(fn);
    }

    // ── Items ────────────────────────────────────────────────────────────

    private positionItem(el: HTMLElement) {
        if (el.dataset.item === undefined) return;
        const x = parseFloat(el.dataset.x ?? '0');
        const y = parseFloat(el.dataset.y ?? '0');
        const w = parseFloat(el.dataset.w ?? '0');
        const h = parseFloat(el.dataset.h ?? '0');
        // A new item may already have been laid out at the origin (anything forcing layout in the
        // same batch does that), so its first coordinates would otherwise animate from (0, 0).
        // Apply them with transitions off, commit, then let animated items fade in where they are.
        const isFresh = this.fresh.has(el) || !el.style.getPropertyValue('--ws-x');
        const entering = isFresh && !this.fresh.has(el);
        if (isFresh) {
            if (entering) {
                this.fresh.add(el);
                requestAnimationFrame(() => requestAnimationFrame(() => this.fresh.delete(el)));
            }
            el.style.transition = 'none';
            if (entering && el.dataset.animate !== undefined) el.style.opacity = '0';
        }
        el.style.setProperty('--ws-x', `${x}px`);
        el.style.setProperty('--ws-y', `${y}px`);
        el.style.width = `${w}px`;
        el.style.height = `${h}px`;
        if (isFresh) {
            void el.offsetWidth; // commit the untransitioned position (and the hidden state)
            el.style.transition = '';
            if (entering) el.style.opacity = ''; // transitions up to whatever the stylesheet says
        }
    }

    private items(): HTMLElement[] {
        return Array.from(this.children).filter((el) => (el as HTMLElement).dataset.item !== undefined) as HTMLElement[];
    }

    private itemRect(el: HTMLElement) {
        return {
            x: parseFloat(el.dataset.x ?? '0'),
            y: parseFloat(el.dataset.y ?? '0'),
            w: parseFloat(el.dataset.w ?? '0'),
            h: parseFloat(el.dataset.h ?? '0'),
        };
    }

    private applyTransform() {
        this.canvas.style.transform = `translate(${this.tx}px, ${this.ty}px) scale(${this.scale})`;
    }

    private clampScale(s: number) {
        return Math.min(this.maxScale, Math.max(this.minScale, s));
    }

    // ── Public API (called from the server) ──────────────────────────────

    /** Place workspace point (cx, cy) at the viewport centre at the given scale. */
    setView(cx: number, cy: number, scale: number) {
        this.anchorPoint(cx, cy, 0.5, 0.5, scale);
    }

    /** Place workspace point (px, py) at viewport fraction (fx, fy) at the given scale. */
    anchorPoint(px: number, py: number, fx: number, fy: number, scale: number) {
        this.whenSized(() => {
            const s = this.clampScale(scale);
            this.animateTo(this.clientWidth * fx - px * s, this.clientHeight * fy - py * s, s);
        });
    }

    /** Keep the current scale and centre on the point. */
    centerOn(cx: number, cy: number) {
        this.whenSized(() => this.animateTo(
            this.clientWidth / 2 - cx * this.scale,
            this.clientHeight / 2 - cy * this.scale,
            this.scale));
    }

    /** Fit the rectangle in the viewport, centred. `pad` is in workspace units. */
    fitRect(x: number, y: number, w: number, h: number, pad: number = 0) {
        this.whenSized(() => {
            const v = this.computeFit(x - pad, y - pad, w + 2 * pad, h + 2 * pad, 0);
            this.animateTo(v.tx, v.ty, v.scale);
        });
    }

    /** Zoom out to show everything, then fit the rectangle. */
    zoomToRect(x: number, y: number, w: number, h: number) {
        this.whenSized(() => {
            const all = this.computeFitAll();
            const go = () => {
                const v = this.computeFit(x, y, w, h, 0);
                this.animateTo(v.tx, v.ty, v.scale);
            };
            if (!all) { go(); return; }
            this.animateTo(all.tx, all.ty, all.scale, go);
        });
    }

    fitAll() {
        this.whenSized(() => {
            const v = this.computeFitAll();
            if (v) this.animateTo(v.tx, v.ty, v.scale);
        });
    }

    panBy(dx: number, dy: number) {
        this.whenSized(() => this.animateTo(this.tx + dx, this.ty + dy, this.scale));
    }

    getView(): { cx: number; cy: number; scale: number } {
        return {
            cx: (this.clientWidth / 2 - this.tx) / this.scale,
            cy: (this.clientHeight / 2 - this.ty) / this.scale,
            scale: this.scale,
        };
    }

    /** Surface coordinates of a client (viewport-relative to the page) point, at the current view. */
    toWorkspace(clientX: number, clientY: number): { x: number; y: number } {
        const rect = this.getBoundingClientRect();
        return {
            x: (clientX - rect.left - this.tx) / this.scale,
            y: (clientY - rect.top - this.ty) / this.scale,
        };
    }

    /** Current scale, for layers that need it synchronously. */
    get currentScale(): number {
        return this.scale;
    }

    /** True when the deepest active element is a text input or similar. */
    deepActiveIsInteractive(): boolean {
        let el: Element | null = document.activeElement;
        while (el?.shadowRoot?.activeElement) el = el.shadowRoot.activeElement;
        if (!el || el === document.body || el === this) return false;
        const tag = el.tagName.toLowerCase();
        return tag === 'input' || tag === 'textarea' || tag === 'select' || (el as HTMLElement).isContentEditable;
    }

    private computeFit(x: number, y: number, w: number, h: number, pad: number): View {
        const vw = this.clientWidth, vh = this.clientHeight;
        const scale = this.clampScale(Math.min((vw - pad * 2) / w, (vh - pad * 2) / h));
        return { tx: vw / 2 - (x + w / 2) * scale, ty: vh / 2 - (y + h / 2) * scale, scale };
    }

    private computeFitAll(): View | null {
        const items = this.items();
        if (items.length === 0) return null;
        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        for (const el of items) {
            const r = this.itemRect(el);
            minX = Math.min(minX, r.x); minY = Math.min(minY, r.y);
            maxX = Math.max(maxX, r.x + r.w); maxY = Math.max(maxY, r.y + r.h);
        }
        return this.computeFit(minX, minY, maxX - minX, maxY - minY, this.fitPadding);
    }

    // ── Animation ────────────────────────────────────────────────────────

    private animateTo(tx: number, ty: number, scale: number, onDone?: () => void) {
        // Drop any previous completion so retargeting never fires stale callbacks.
        if (this.pendingOnEnd) {
            this.canvas.removeEventListener('transitionend', this.pendingOnEnd);
            this.pendingOnEnd = undefined;
            this.pendingDone = undefined;
        }
        const unchanged = Math.abs(tx - this.tx) < 0.5 && Math.abs(ty - this.ty) < 0.5 && Math.abs(scale - this.scale) < 0.001;
        if (unchanged || this._transitionDuration <= 0) {
            this.tx = tx; this.ty = ty; this.scale = scale;
            this.canvas.style.transition = '';
            this.applyTransform();
            this.dispatchViewChanged();
            onDone?.();
            return;
        }
        this.canvas.style.transition = `transform var(--ws-duration) var(--ws-easing)`;
        this.tx = tx; this.ty = ty; this.scale = scale;
        this.applyTransform();

        const onEnd = (e?: Event) => {
            if (e && e.target !== this.canvas) return;
            this.canvas.style.transition = '';
            this.canvas.removeEventListener('transitionend', onEnd);
            this.pendingOnEnd = undefined;
            const done = this.pendingDone;
            this.pendingDone = undefined;
            this.dispatchViewChanged();
            done?.();
        };
        this.pendingOnEnd = onEnd;
        this.pendingDone = onDone;
        this.canvas.addEventListener('transitionend', onEnd);
    }

    private dispatchViewChanged() {
        const v = this.getView();
        this.dispatchEvent(new CustomEvent('workspace-view-changed', {
            bubbles: true, composed: true, detail: v,
        }));
    }

    /** Debounced view-changed after pointer gestures. */
    private scheduleSettle() {
        if (this.settleTimer) window.clearTimeout(this.settleTimer);
        this.settleTimer = window.setTimeout(() => {
            this.settleTimer = undefined;
            this.dispatchViewChanged();
        }, 150);
    }

    // ── Pointer gestures ─────────────────────────────────────────────────

    private isInteractive(e: Event): boolean {
        for (const node of e.composedPath()) {
            if (node === this) break;
            if (node instanceof Element) {
                const tag = node.tagName.toLowerCase();
                if (tag === 'input' || tag === 'textarea' || tag === 'select' || tag === 'button' || tag === 'a') return true;
                if ((node as HTMLElement).isContentEditable) return true;
            }
        }
        return false;
    }

    private onMouseDown = (e: MouseEvent) => {
        if (e.button !== 0 || !this.draggingEnabled || this.isInteractive(e)) return;
        this.focus();
        this.isDragging = true;
        this.dragged = false;
        this.style.cursor = 'grabbing';
        e.preventDefault();
    };

    private onMouseMove = (e: MouseEvent) => {
        if (!this.isDragging) return;
        this.dragged = true;
        this.tx += e.movementX;
        this.ty += e.movementY;
        this.applyTransform();
    };

    private onMouseUp = () => {
        if (!this.isDragging) return;
        this.isDragging = false;
        this.style.cursor = '';
        if (this.dragged) this.scheduleSettle();
    };

    private onWheel = (e: WheelEvent) => {
        if (!this.zoomingEnabled) return;
        e.preventDefault();
        const rect = this.getBoundingClientRect();
        const vx = e.clientX - rect.left, vy = e.clientY - rect.top;
        const cx = (vx - this.tx) / this.scale, cy = (vy - this.ty) / this.scale;
        const newScale = this.clampScale(this.scale * (e.deltaY < 0 ? 1.1 : 1 / 1.1));
        this.tx = vx - cx * newScale;
        this.ty = vy - cy * newScale;
        this.scale = newScale;
        this.applyTransform();
        this.scheduleSettle();
    };

    private onTouchStart = (e: TouchEvent) => {
        if (!this.draggingEnabled && !this.zoomingEnabled) return;
        if (this.isInteractive(e)) return;
        e.preventDefault();
        this.touches = Array.from(e.touches);
        if (this.touches.length === 2) this.lastPinchDist = this.pinchDist(this.touches[0], this.touches[1]);
    };

    private onTouchMove = (e: TouchEvent) => {
        if (!this.draggingEnabled && !this.zoomingEnabled) return;
        e.preventDefault();
        const t = Array.from(e.touches);
        if (t.length === 1 && this.touches.length === 1 && this.draggingEnabled) {
            this.tx += t[0].clientX - this.touches[0].clientX;
            this.ty += t[0].clientY - this.touches[0].clientY;
            this.applyTransform();
        } else if (t.length === 2 && this.touches.length >= 1) {
            const newDist = this.pinchDist(t[0], t[1]);
            const oldDist = this.lastPinchDist || newDist;
            const midX = (t[0].clientX + t[1].clientX) / 2, midY = (t[0].clientY + t[1].clientY) / 2;
            const rect = this.getBoundingClientRect();
            const vx = midX - rect.left, vy = midY - rect.top;
            const cx = (vx - this.tx) / this.scale, cy = (vy - this.ty) / this.scale;
            const oldMidX = this.touches.length >= 2 ? (this.touches[0].clientX + this.touches[1].clientX) / 2 : this.touches[0].clientX;
            const oldMidY = this.touches.length >= 2 ? (this.touches[0].clientY + this.touches[1].clientY) / 2 : this.touches[0].clientY;
            const factor = (oldDist > 0 && this.zoomingEnabled) ? newDist / oldDist : 1;
            const newScale = this.clampScale(this.scale * factor);
            this.tx = vx - cx * newScale + (this.draggingEnabled ? midX - oldMidX : 0);
            this.ty = vy - cy * newScale + (this.draggingEnabled ? midY - oldMidY : 0);
            this.scale = newScale;
            this.lastPinchDist = newDist;
            this.applyTransform();
        }
        this.touches = t;
    };

    private onTouchEnd = (e: TouchEvent) => {
        this.touches = Array.from(e.touches);
        if (this.touches.length < 2) this.lastPinchDist = 0;
        if (this.touches.length === 0) this.scheduleSettle();
    };

    private pinchDist(a: Touch, b: Touch) {
        const dx = a.clientX - b.clientX, dy = a.clientY - b.clientY;
        return Math.sqrt(dx * dx + dy * dy);
    }
}

customElements.define('workspace-viewport', WorkspaceViewport);
