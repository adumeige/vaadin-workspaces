class GridWorkspaceView extends HTMLElement {
    private canvas!: HTMLDivElement;
    private tx: number = 0;
    private ty: number = 0;
    private scale: number = 1.0;
    private isDragging: boolean = false;
    private lastMouseX: number = 0;
    private lastMouseY: number = 0;
    private touches: Touch[] = [];
    private lastPinchDist: number = 0;
    private observer!: MutationObserver;

    get cellWidth(): number {
        return (this as any)._cellWidth ?? 320;
    }
    set cellWidth(v: number) {
        (this as any)._cellWidth = v;
    }
    get cellHeight(): number {
        return (this as any)._cellHeight ?? 240;
    }
    set cellHeight(v: number) {
        (this as any)._cellHeight = v;
    }
    get cellMargin(): number {
        return (this as any)._cellMargin ?? 0;
    }
    set cellMargin(v: number) {
        (this as any)._cellMargin = v;
    }
    get initialCol(): number | undefined {
        return (this as any)._initialCol;
    }
    set initialCol(v: number) {
        (this as any)._initialCol = v;
    }
    get initialRow(): number | undefined {
        return (this as any)._initialRow;
    }
    set initialRow(v: number) {
        (this as any)._initialRow = v;
    }
    get initialFitAll(): boolean {
        return (this as any)._initialFitAll ?? false;
    }
    set initialFitAll(v: boolean) {
        (this as any)._initialFitAll = v;
    }
    get draggingEnabled(): boolean {
        return (this as any)._draggingEnabled ?? true;
    }
    set draggingEnabled(v: boolean) {
        (this as any)._draggingEnabled = v;
    }
    get zoomingEnabled(): boolean {
        return (this as any)._zoomingEnabled ?? true;
    }
    set zoomingEnabled(v: boolean) {
        (this as any)._zoomingEnabled = v;
    }

    connectedCallback() {
        this.style.display = 'block';
        this.style.position = 'relative';
        this.style.overflow = 'hidden';
        this.tabIndex = 0;

        // Create the canvas div
        this.canvas = document.createElement('div');
        this.canvas.style.position = 'absolute';
        this.canvas.style.transformOrigin = '0 0';
        this.canvas.style.willChange = 'transform';
        this.applyTransform();

        // Move any existing [data-cell] children into the canvas
        const existingCells = Array.from(this.children).filter(
            (el) => el !== this.canvas && (el as HTMLElement).dataset.cell === 'true'
        ) as HTMLElement[];
        existingCells.forEach((el) => this.positionCell(el));

        this.appendChild(this.canvas);
        existingCells.forEach((el) => this.canvas.appendChild(el));

        // Observe future children added by Vaadin server
        this.observer = new MutationObserver((mutations) => {
            for (const mutation of mutations) {
                mutation.addedNodes.forEach((node) => {
                    if (node === this.canvas) return;
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        const el = node as HTMLElement;
                        if (el.dataset.cell === 'true') {
                            this.positionCell(el);
                            this.canvas.appendChild(el);
                        }
                    }
                });
            }
        });
        this.observer.observe(this, { childList: true });

        // Mouse events
        this.addEventListener('mousedown', this.onMouseDown.bind(this));
        this.addEventListener('mousemove', this.onMouseMove.bind(this));
        this.addEventListener('mouseup', this.onMouseUp.bind(this));
        this.addEventListener('mouseleave', this.onMouseUp.bind(this));
        this.addEventListener('wheel', this.onWheel.bind(this), { passive: false });

        // Touch events
        this.addEventListener('touchstart', this.onTouchStart.bind(this), { passive: false });
        this.addEventListener('touchmove', this.onTouchMove.bind(this), { passive: false });
        this.addEventListener('touchend', this.onTouchEnd.bind(this), { passive: false });

        // Keyboard events
        this.addEventListener('keydown', this.onKeyDown.bind(this));

        // Apply initial view once the element has been laid out
        const hasFitAll = this.initialFitAll;
        const hasFitCell = this.initialCol !== undefined && this.initialRow !== undefined;
        if (hasFitAll || hasFitCell) {
            const ro = new ResizeObserver(() => {
                if (this.clientWidth > 0) {
                    ro.disconnect();
                    if (hasFitAll) this.fitAll();
                    else this.fitToCell(this.initialCol!, this.initialRow!);
                }
            });
            ro.observe(this);
        }
    }

    disconnectedCallback() {
        if (this.observer) {
            this.observer.disconnect();
        }
    }

    private positionCell(el: HTMLElement) {
        const col = parseInt(el.dataset.col ?? '0', 10);
        const row = parseInt(el.dataset.row ?? '0', 10);
        el.style.position = 'absolute';
        el.style.left = col * (this.cellWidth + this.cellMargin) + 'px';
        el.style.top = row * (this.cellHeight + this.cellMargin) + 'px';
        el.style.width = this.cellWidth + 'px';
        el.style.height = this.cellHeight + 'px';
        el.style.overflow = 'hidden';
        el.style.boxSizing = 'border-box';
    }

    private applyTransform() {
        this.canvas.style.transform = `translate(${this.tx}px, ${this.ty}px) scale(${this.scale})`;
    }

    // ── Mouse ──────────────────────────────────────────────────────────────

    private onMouseDown(e: MouseEvent) {
        if (e.button !== 0) return;
        if (this.isInteractive(e)) return;
        if (!this.draggingEnabled) return;
        this.focus();
        this.isDragging = true;
        this.lastMouseX = e.clientX;
        this.lastMouseY = e.clientY;
        this.style.cursor = 'grabbing';
        e.preventDefault();
    }

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

    deepActiveIsInteractive(): boolean {
        let el: Element | null = document.activeElement;
        while (el?.shadowRoot?.activeElement) {
            el = el.shadowRoot.activeElement;
        }
        if (!el || el === document.body || el === this) return false;
        const tag = el.tagName.toLowerCase();
        return tag === 'input' || tag === 'textarea' || tag === 'select'
            || (el as HTMLElement).isContentEditable;
    }

    private onMouseMove(e: MouseEvent) {
        if (!this.isDragging) return;
        this.tx += e.movementX;
        this.ty += e.movementY;
        this.applyTransform();
    }

    private onMouseUp(_e: MouseEvent) {
        this.isDragging = false;
        this.style.cursor = '';
    }

    // ── Wheel / Zoom ───────────────────────────────────────────────────────

    private onWheel(e: WheelEvent) {
        if (!this.zoomingEnabled) return;
        e.preventDefault();
        const rect = this.getBoundingClientRect();
        // Cursor position relative to viewport element
        const cursorVX = e.clientX - rect.left;
        const cursorVY = e.clientY - rect.top;
        // Cursor position in canvas space before zoom
        const cursorCX = (cursorVX - this.tx) / this.scale;
        const cursorCY = (cursorVY - this.ty) / this.scale;

        const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
        const newScale = Math.min(4.0, Math.max(0.05, this.scale * factor));

        this.tx = cursorVX - cursorCX * newScale;
        this.ty = cursorVY - cursorCY * newScale;
        this.scale = newScale;
        this.applyTransform();
    }

    // ── Touch ──────────────────────────────────────────────────────────────

    private onTouchStart(e: TouchEvent) {
        e.preventDefault();
        this.touches = Array.from(e.touches);
        if (this.touches.length === 2) {
            this.lastPinchDist = this.pinchDist(this.touches[0], this.touches[1]);
        }
    }

    private onTouchMove(e: TouchEvent) {
        e.preventDefault();
        const newTouches = Array.from(e.touches);

        if (newTouches.length === 1 && this.touches.length === 1 && this.draggingEnabled) {
            // Single-finger pan
            const dx = newTouches[0].clientX - this.touches[0].clientX;
            const dy = newTouches[0].clientY - this.touches[0].clientY;
            this.tx += dx;
            this.ty += dy;
            this.applyTransform();
        } else if (newTouches.length === 2 && this.touches.length >= 1 && (this.draggingEnabled || this.zoomingEnabled)) {
            // Pinch zoom + pan
            const newDist = this.pinchDist(newTouches[0], newTouches[1]);
            const oldDist = this.lastPinchDist || newDist;

            // Midpoint of the two fingers in viewport space
            const midX = (newTouches[0].clientX + newTouches[1].clientX) / 2;
            const midY = (newTouches[0].clientY + newTouches[1].clientY) / 2;
            const rect = this.getBoundingClientRect();
            const cursorVX = midX - rect.left;
            const cursorVY = midY - rect.top;

            const cursorCX = (cursorVX - this.tx) / this.scale;
            const cursorCY = (cursorVY - this.ty) / this.scale;

            // Pan from midpoint movement
            const oldMidX = this.touches.length >= 2
                ? (this.touches[0].clientX + this.touches[1].clientX) / 2
                : this.touches[0].clientX;
            const oldMidY = this.touches.length >= 2
                ? (this.touches[0].clientY + this.touches[1].clientY) / 2
                : this.touches[0].clientY;
            const panDX = midX - oldMidX;
            const panDY = midY - oldMidY;

            const factor = (oldDist > 0 && this.zoomingEnabled) ? newDist / oldDist : 1;
            const newScale = Math.min(4.0, Math.max(0.05, this.scale * factor));

            this.tx = cursorVX - cursorCX * newScale + (this.draggingEnabled ? panDX : 0);
            this.ty = cursorVY - cursorCY * newScale + (this.draggingEnabled ? panDY : 0);
            this.scale = newScale;
            this.lastPinchDist = newDist;
            this.applyTransform();
        }

        this.touches = newTouches;
    }

    private onTouchEnd(e: TouchEvent) {
        this.touches = Array.from(e.touches);
        if (this.touches.length < 2) {
            this.lastPinchDist = 0;
        }
    }

    private pinchDist(t1: Touch, t2: Touch): number {
        const dx = t1.clientX - t2.clientX;
        const dy = t1.clientY - t2.clientY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ── Keyboard ───────────────────────────────────────────────────────────

    private onKeyDown(e: KeyboardEvent) {
        if (e.key === 'Escape') {
            (document.activeElement as HTMLElement)?.blur?.();
            this.focus();
            return;
        }
        if (this.deepActiveIsInteractive()) return;
        switch (e.key) {
            case 'ArrowLeft':  e.preventDefault(); e.stopPropagation(); this.moveLeft();  break;
            case 'ArrowRight': e.preventDefault(); e.stopPropagation(); this.moveRight(); break;
            case 'ArrowUp':    e.preventDefault(); e.stopPropagation(); this.moveUp();    break;
            case 'ArrowDown':  e.preventDefault(); e.stopPropagation(); this.moveDown();  break;
        }
    }

    moveLeft()  { this.moveToAdjacentCell(-1,  0); }
    moveRight() { this.moveToAdjacentCell(+1,  0); }
    moveUp()    { this.moveToAdjacentCell( 0, -1); }
    moveDown()  { this.moveToAdjacentCell( 0, +1); }

    private moveToAdjacentCell(dcol: number, drow: number) {
        const cells = Array.from(this.canvas.querySelectorAll<HTMLElement>('[data-cell]'));
        if (cells.length === 0) return;

        // Infer current cell from viewport centre
        const cw = this.cellWidth, ch = this.cellHeight, cm = this.cellMargin;
        const vCenterX = (this.clientWidth  / 2 - this.tx) / this.scale;
        const vCenterY = (this.clientHeight / 2 - this.ty) / this.scale;
        const currentCol = Math.round((vCenterX - cw / 2) / (cw + cm));
        const currentRow = Math.round((vCenterY - ch / 2) / (ch + cm));

        // Clamp target to grid extent
        const cols = cells.map(el => parseInt(el.dataset.col ?? '0', 10));
        const rows = cells.map(el => parseInt(el.dataset.row ?? '0', 10));
        const targetCol = Math.min(Math.max(...cols), Math.max(Math.min(...cols), currentCol + dcol));
        const targetRow = Math.min(Math.max(...rows), Math.max(Math.min(...rows), currentRow + drow));

        // Centre that cell at the current scale
        const centerX = targetCol * (cw + cm) + cw / 2;
        const centerY = targetRow * (ch + cm) + ch / 2;
        this.animateTo(
            this.clientWidth  / 2 - centerX * this.scale,
            this.clientHeight / 2 - centerY * this.scale,
            this.scale
        );
    }

    // ── Programmatic API ───────────────────────────────────────────────────

    fitToCell(col: number, row: number) {
        if (!this.clientWidth) return;
        const p = this.computeFitToCell(col, row);
        this.animateTo(p.tx, p.ty, p.scale);
    }

    zoomToCell(col: number, row: number) {
        if (!this.clientWidth) return;
        const zoomOut = this.computeFitAll();
        if (!zoomOut) return;
        this.animateTo(zoomOut.tx, zoomOut.ty, zoomOut.scale, () => {
            const p = this.computeFitToCell(col, row);
            this.animateTo(p.tx, p.ty, p.scale);
        });
    }

    fitAll() {
        if (!this.clientWidth) return;
        const p = this.computeFitAll();
        if (!p) return;
        this.animateTo(p.tx, p.ty, p.scale);
    }

    private computeFitToCell(col: number, row: number): { tx: number; ty: number; scale: number } {
        const vw = this.clientWidth;
        const vh = this.clientHeight;
        const cw = this.cellWidth;
        const ch = this.cellHeight;
        const cm = this.cellMargin;
        const scale = Math.min(4.0, Math.max(0.05, Math.min(vw / cw, vh / ch)));
        const centerX = col * (cw + cm) + cw / 2;
        const centerY = row * (ch + cm) + ch / 2;
        return { tx: vw / 2 - centerX * scale, ty: vh / 2 - centerY * scale, scale };
    }

    private computeFitAll(): { tx: number; ty: number; scale: number } | null {
        const cells = Array.from(this.canvas.querySelectorAll<HTMLElement>('[data-cell]'));
        if (cells.length === 0) return null;

        const pad = 40;
        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        cells.forEach((el) => {
            const col = parseInt(el.dataset.col ?? '0', 10);
            const row = parseInt(el.dataset.row ?? '0', 10);
            const x = col * (this.cellWidth + this.cellMargin);
            const y = row * (this.cellHeight + this.cellMargin);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + this.cellWidth);
            maxY = Math.max(maxY, y + this.cellHeight);
        });

        const vw = this.clientWidth;
        const vh = this.clientHeight;
        const scale = Math.min(4.0, Math.max(0.05,
            Math.min((vw - pad * 2) / (maxX - minX), (vh - pad * 2) / (maxY - minY))
        ));
        const centerX = (minX + maxX) / 2;
        const centerY = (minY + maxY) / 2;
        return { tx: vw / 2 - centerX * scale, ty: vh / 2 - centerY * scale, scale };
    }

    private dispatchCellFocusedEvent() {
        const cw = this.cellWidth, ch = this.cellHeight, cm = this.cellMargin;
        const col = Math.round(((this.clientWidth  / 2 - this.tx) / this.scale - cw / 2) / (cw + cm));
        const row = Math.round(((this.clientHeight / 2 - this.ty) / this.scale - ch / 2) / (ch + cm));
        this.dispatchEvent(new CustomEvent('workspace-cell-focused', {
            bubbles: true, composed: true, detail: { col, row }
        }));
    }

    private animateTo(tx: number, ty: number, scale: number, onDone?: () => void) {
        const unchanged = Math.abs(tx - this.tx) < 0.5
            && Math.abs(ty - this.ty) < 0.5
            && Math.abs(scale - this.scale) < 0.001;
        if (unchanged) {
            onDone?.();
            return;
        }
        this.canvas.style.transition = 'transform 400ms cubic-bezier(0.4,0,0.2,1)';
        this.tx = tx;
        this.ty = ty;
        this.scale = scale;
        this.applyTransform();

        const onEnd = () => {
            this.canvas.style.transition = '';
            this.canvas.removeEventListener('transitionend', onEnd);
            this.dispatchCellFocusedEvent();
            onDone?.();
        };
        this.canvas.addEventListener('transitionend', onEnd);
    }
}

customElements.define('grid-workspace-view', GridWorkspaceView);
