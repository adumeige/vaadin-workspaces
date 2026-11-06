/**
 * Annotation layer for workspace-viewport. Renders flat geometry sent by the server and lets the
 * user draw with a set of tools; drawn shapes are reported back with the id of the item under
 * each point so the server can anchor them.
 *
 * Coordinates are surface (workspace) units: the layer is a child of the viewport's transformed
 * canvas, so it pans and zooms with the items for free.
 */

const SVG = 'http://www.w3.org/2000/svg';
const XHTML = 'http://www.w3.org/1999/xhtml';

type Style = { color: string; width: number; opacity: number; dashed: boolean; render?: 'PLAIN' | 'SKETCHY' };
type Pt = { x: number; y: number; itemId?: number };
type Tool = 'NONE' | 'ELLIPSE' | 'RECT' | 'ARROW' | 'PEN' | 'MARKER' | 'TEXT_MARKER' | 'TEXT' | 'NOTE' | 'ERASER';
const ARROW_ENDS = ['NONE', 'ARROW', 'DIAMOND_OPEN', 'DIAMOND_FILLED', 'CIRCLE_OPEN', 'CIRCLE_FILLED', 'TRIANGLE_OPEN', 'TRIANGLE_FILLED'] as const;
type ArrowEnd = typeof ARROW_ENDS[number];
function arrowEnd(value: unknown, fallback: ArrowEnd): ArrowEnd {
    return ARROW_ENDS.includes(value as ArrowEnd) ? value as ArrowEnd : fallback;
}
let nextArrowMaskId = 0;

interface Viewport extends HTMLElement {
    toWorkspace(cx: number, cy: number): { x: number; y: number };
    readonly currentScale: number;
    draggingEnabled: boolean;
}

const DEFAULT_STYLE: Style = { color: '#e53e3e', width: 3, opacity: 1, dashed: false, render: 'PLAIN' };

/** Controls relative to the anchor chord, so curves rotate and resize with their anchors. */
function arrowControls(x1: number, y1: number, x2: number, y2: number, bends: number): [Pt, Pt] {
    const dx = x2 - x1, dy = y2 - y1;
    const offset = bends === 2 ? 0.6 : 0.4;
    const side = bends === 2 ? -1 : 1;
    return [
        { x: x1 + dx / 3 - dy * offset, y: y1 + dy / 3 + dx * offset },
        { x: x1 + dx * 2 / 3 - dy * offset * side, y: y1 + dy * 2 / 3 + dx * offset * side },
    ];
}

function curvePath(x1: number, y1: number, x2: number, y2: number, c1: Pt, c2: Pt): string {
    return `M ${x1} ${y1} C ${c1.x} ${c1.y} ${c2.x} ${c2.y} ${x2} ${y2}`;
}

// ── Hand-drawn rendering ─────────────────────────────────────────────────
// A compact take on the technique popularised by rough.js (MIT): every stroke is drawn twice
// with small, seeded random offsets on its bezier control points, closed shapes are wobbly
// polylines, and fills are hachure lines. Seeding by doodle id keeps a shape stable across
// re-renders instead of jittering on every push.

function seeded(seed: string): () => number {
    let h = 2166136261;
    for (let i = 0; i < seed.length; i++) h = Math.imul(h ^ seed.charCodeAt(i), 16777619);
    return () => {
        h += 0x6D2B79F5;
        let t = Math.imul(h ^ (h >>> 15), 1 | h);
        t ^= t + Math.imul(t ^ (t >>> 7), 61 | t);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

class Sketch {
    constructor(private rand: () => number, private roughness = 1) {}

    private off(amount: number) { return (this.rand() * 2 - 1) * amount * this.roughness; }

    /** One hand-drawn pass of a straight line as a cubic bezier. */
    private linePass(x1: number, y1: number, x2: number, y2: number, scaleOffset: number): string {
        const len = Math.hypot(x2 - x1, y2 - y1);
        const o = Math.min(len * 0.03, 6) * scaleOffset;
        const sx = x1 + this.off(o), sy = y1 + this.off(o);
        const ex = x2 + this.off(o), ey = y2 + this.off(o);
        const c1x = x1 + (x2 - x1) * 0.35 + this.off(o * 2), c1y = y1 + (y2 - y1) * 0.35 + this.off(o * 2);
        const c2x = x1 + (x2 - x1) * 0.7 + this.off(o * 2), c2y = y1 + (y2 - y1) * 0.7 + this.off(o * 2);
        return `M ${sx} ${sy} C ${c1x} ${c1y} ${c2x} ${c2y} ${ex} ${ey}`;
    }

    line(x1: number, y1: number, x2: number, y2: number, passes = 2): string {
        let d = this.linePass(x1, y1, x2, y2, 1);
        for (let i = 1; i < passes; i++) d += ' ' + this.linePass(x1, y1, x2, y2, 0.6);
        return d;
    }

    /** Keep the anchor endpoints exact; only the curve's controls receive seeded wobble. */
    curve(x1: number, y1: number, x2: number, y2: number, c1: Pt, c2: Pt): string {
        const o = Math.min(Math.hypot(x2 - x1, y2 - y1) * 0.03, 6);
        return [1, 0.6].map(scale => curvePath(x1, y1, x2, y2,
            { x: c1.x + this.off(o * scale), y: c1.y + this.off(o * scale) },
            { x: c2.x + this.off(o * scale), y: c2.y + this.off(o * scale) })).join(' ');
    }

    /** Wobbly closed ellipse, drawn twice with a small overshoot like a real pen. */
    ellipse(cx: number, cy: number, rx: number, ry: number): string {
        const n = Math.max(16, Math.min(64, Math.round((rx + ry) / 6)));
        const passes: string[] = [];
        for (let p = 0; p < 2; p++) {
            const start = this.rand() * Math.PI * 2;
            const jitter = Math.min(rx, ry) * 0.04 + 1;
            const pts: [number, number][] = [];
            for (let i = 0; i <= n + 1; i++) {
                const a = start + (i / n) * Math.PI * 2;
                pts.push([cx + Math.cos(a) * (rx + this.off(jitter)), cy + Math.sin(a) * (ry + this.off(jitter))]);
            }
            passes.push(this.smooth(pts));
        }
        return passes.join(' ');
    }

    /** Quadratic smoothing through midpoints. */
    smooth(pts: [number, number][]): string {
        if (pts.length < 2) return '';
        let d = `M ${pts[0][0]} ${pts[0][1]}`;
        for (let i = 1; i < pts.length - 1; i++) {
            const mx = (pts[i][0] + pts[i + 1][0]) / 2, my = (pts[i][1] + pts[i + 1][1]) / 2;
            d += ` Q ${pts[i][0]} ${pts[i][1]} ${mx} ${my}`;
        }
        const l = pts[pts.length - 1];
        d += ` L ${l[0]} ${l[1]}`;
        return d;
    }

    /** A freehand polyline with slight tremor, drawn once (plus a faint second pass by the caller). */
    polyline(pts: Pt[], tremor: number): string {
        const out: [number, number][] = pts.map((p) => [p.x + this.off(tremor), p.y + this.off(tremor)]);
        return this.smooth(out);
    }

    rectOutline(x: number, y: number, w: number, h: number): string {
        return [
            this.line(x, y, x + w, y), this.line(x + w, y, x + w, y + h),
            this.line(x + w, y + h, x, y + h), this.line(x, y + h, x, y),
        ].join(' ');
    }

    /** 45-degree hachure lines clipped to the rectangle. */
    hachure(x: number, y: number, w: number, h: number, gap: number): string {
        const parts: string[] = [];
        const g = Math.max(gap, 2);
        for (let t = -h; t < w; t += g) {
            const s = Math.max(0, -t), e = Math.min(h, w - t);
            if (e - s < 1) continue;
            const jt = this.off(g * 0.3);
            parts.push(this.linePass(x + t + s + jt, y + s, x + t + e + jt, y + e, 0.5));
        }
        return parts.join(' ');
    }
}

class WorkspaceDoodleLayer extends HTMLElement {
    private svg!: SVGSVGElement;
    private catcher!: SVGRectElement;
    private shapes!: SVGGElement;
    private preview!: SVGGElement;
    private rendered = new Map<string, { json: string; el: SVGElement }>();
    private _doodles: any[] = [];
    private _tool: Tool = 'NONE';
    private _drawStyle: Style = DEFAULT_STYLE;
    private _arrowBends = 0;
    private _arrowStart: ArrowEnd = 'NONE';
    private _arrowEnd: ArrowEnd = 'ARROW';
    private drawing?: { start: Pt; points: Pt[]; el?: SVGElement };
    private editor?: SVGForeignObjectElement;
    private savedDragging?: boolean;
    private reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

    // ── Properties (from the server) ─────────────────────────────────────

    get doodles() { return this._doodles; }
    set doodles(v: any[]) {
        this._doodles = Array.isArray(v) ? v : [];
        if (this.shapes) this.render();
    }

    get tool() { return this._tool; }
    set tool(v: Tool) {
        if (v === this._tool) return;
        this._tool = v ?? 'NONE';
        if (this.svg) this.applyTool();
    }

    get drawStyle() { return this._drawStyle; }
    set drawStyle(v: Style) { this._drawStyle = { ...DEFAULT_STYLE, ...(v ?? {}) }; }

    get arrowBends() { return this._arrowBends; }
    set arrowBends(v: number) { this._arrowBends = v === 1 || v === 2 ? v : 0; }

    get arrowStart() { return this._arrowStart; }
    set arrowStart(v: ArrowEnd) { this._arrowStart = arrowEnd(v, 'NONE'); }
    get arrowEnd() { return this._arrowEnd; }
    set arrowEnd(v: ArrowEnd) { this._arrowEnd = arrowEnd(v, 'ARROW'); }

    // ── Lifecycle ────────────────────────────────────────────────────────

    connectedCallback() {
        this.style.position = 'absolute';
        this.style.left = '0';
        this.style.top = '0';
        this.style.width = '0';
        this.style.height = '0';
        this.style.overflow = 'visible';

        if (!this.svg) {
            this.svg = document.createElementNS(SVG, 'svg');
            this.svg.setAttribute('width', '1');
            this.svg.setAttribute('height', '1');
            this.svg.style.overflow = 'visible';
            this.svg.style.position = 'absolute';
            this.svg.style.left = '0';
            this.svg.style.top = '0';
            // The root svg is 1x1 and only receives events inside its box, so a huge transparent
            // rect catches pointer events over the whole surface while a drawing tool is active.
            this.catcher = document.createElementNS(SVG, 'rect');
            this.catcher.setAttribute('x', '-1000000'); this.catcher.setAttribute('y', '-1000000');
            this.catcher.setAttribute('width', '2000000'); this.catcher.setAttribute('height', '2000000');
            this.catcher.setAttribute('fill', 'none');
            this.catcher.style.pointerEvents = 'none';
            this.shapes = document.createElementNS(SVG, 'g');
            this.preview = document.createElementNS(SVG, 'g');
            this.preview.style.pointerEvents = 'none';
            this.svg.append(this.catcher, this.shapes, this.preview);
            this.appendChild(this.svg);
        }
        this.svg.addEventListener('pointerdown', this.onPointerDown);
        this.svg.addEventListener('pointermove', this.onPointerMove);
        this.svg.addEventListener('pointerup', this.onPointerUp);
        this.svg.addEventListener('pointercancel', this.onPointerUp);
        this.shapes.addEventListener('click', this.onShapeClick);
        document.addEventListener('mouseup', this.onDocumentMouseUp);
        document.addEventListener('keydown', this.onKeyDown);
        this.applyTool();
        this.render();
    }

    disconnectedCallback() {
        this.svg.removeEventListener('pointerdown', this.onPointerDown);
        this.svg.removeEventListener('pointermove', this.onPointerMove);
        this.svg.removeEventListener('pointerup', this.onPointerUp);
        this.svg.removeEventListener('pointercancel', this.onPointerUp);
        this.shapes.removeEventListener('click', this.onShapeClick);
        document.removeEventListener('mouseup', this.onDocumentMouseUp);
        document.removeEventListener('keydown', this.onKeyDown);
        this.restoreDragging();
    }

    private viewport(): Viewport | null {
        return this.closest('workspace-viewport') as Viewport | null;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    private render() {
        const seen = new Set<string>();
        for (const d of this._doodles) {
            if (!d || !d.id) continue;
            seen.add(d.id);
            const json = JSON.stringify(d);
            const existing = this.rendered.get(d.id);
            if (existing && existing.json === json) continue;
            const el = this.build(d);
            if (!el) continue;
            if (existing) existing.el.replaceWith(el);
            else {
                this.shapes.appendChild(el);
                if (!this.reducedMotion) el.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 150 });
            }
            this.rendered.set(d.id, { json, el });
        }
        for (const [id, r] of this.rendered) {
            if (!seen.has(id)) { r.el.remove(); this.rendered.delete(id); }
        }
        // A fresh render means the server has caught up with any preview.
        this.preview.replaceChildren();
    }

    private build(d: any): SVGElement | null {
        const s: Style = { ...DEFAULT_STYLE, ...(d.style ?? {}) };
        let el: SVGElement | null = null;
        if (s.render === 'SKETCHY') el = this.buildSketchy(d, s);
        else switch (d.type) {
            case 'ellipse': el = this.ellipse(d.cx, d.cy, d.rx, d.ry, s); break;
            case 'rect': el = this.rect(d.x, d.y, d.w, d.h, s); break;
            case 'arrow': el = this.arrow(d.x1, d.y1, d.x2, d.y2, s, d.bends, d.startEnd, d.endEnd); break;
            case 'stroke': el = this.path(d.points, s, false); break;
            case 'marker': el = this.path(d.points, s, true); break;
            case 'textHighlight': {
                const g = document.createElementNS(SVG, 'g');
                for (const r of d.rects ?? []) g.appendChild(this.rect(r.x, r.y, r.w, r.h, s));
                el = g;
                break;
            }
            case 'text': el = this.text(d.x, d.y, d.text, s); break;
            case 'note': el = this.note(d.x, d.y, d.w, d.h, d.text, s); break;
            default: return null;
        }
        if (!el) return null;
        el.setAttribute('data-doodle-id', d.id);
        el.setAttribute('data-doodle-type', d.type);
        if (s.render === 'SKETCHY') el.setAttribute('data-doodle-render', 'sketchy');
        el.style.pointerEvents = 'auto';
        el.style.cursor = 'pointer';
        return el;
    }

    // ── Sketchy variants ─────────────────────────────────────────────────

    private sketchPath(d: string, s: Style, opacity = s.opacity, width = s.width): SVGPathElement {
        const p = document.createElementNS(SVG, 'path');
        p.setAttribute('d', d);
        this.outline(p, { ...s, opacity, width });
        return p;
    }

    private buildSketchy(d: any, s: Style): SVGElement | null {
        const sk = new Sketch(seeded(String(d.id)));
        const g = document.createElementNS(SVG, 'g');
        switch (d.type) {
            case 'ellipse':
                g.appendChild(this.sketchPath(sk.ellipse(d.cx, d.cy, d.rx, d.ry), s));
                break;
            case 'rect':
                g.appendChild(this.sketchPath(sk.hachure(d.x, d.y, d.w, d.h, Math.max(6, s.width * 3)), s, Math.min(1, s.opacity * 2), 1.5));
                g.appendChild(this.sketchPath(sk.rectOutline(d.x, d.y, d.w, d.h), s, Math.min(1, s.opacity * 2), 1.5));
                break;
            case 'arrow':
                return this.arrow(d.x1, d.y1, d.x2, d.y2, s, d.bends, d.startEnd, d.endEnd, sk);
            case 'stroke':
                g.appendChild(this.sketchPath(sk.polyline(d.points ?? [], 0.8), s));
                g.appendChild(this.sketchPath(sk.polyline(d.points ?? [], 1.6), s, s.opacity * 0.35, s.width * 0.7));
                break;
            case 'marker': {
                const p1 = this.path((d.points ?? []).map((q: Pt) => q), s, true);
                p1.setAttribute('d', sk.polyline(d.points ?? [], s.width * 0.12));
                const p2 = this.path(d.points ?? [], { ...s, opacity: s.opacity * 0.5, width: s.width * 0.8 }, true);
                p2.setAttribute('d', sk.polyline(d.points ?? [], s.width * 0.25));
                g.append(p1, p2);
                break;
            }
            case 'textHighlight':
                for (const r of d.rects ?? []) {
                    g.appendChild(this.sketchPath(sk.hachure(r.x, r.y, r.w, r.h, 5), s, Math.min(1, s.opacity * 2), 2));
                }
                break;
            case 'text': {
                const el = this.text(d.x, d.y, d.text, s);
                (el.firstElementChild as HTMLElement).classList.add('doodle-sketchy');
                return el;
            }
            case 'note': {
                const tilt = (seeded(String(d.id))() * 2 - 1) * 2;
                const fo = this.note(d.x, d.y, d.w, d.h, d.text, s);
                (fo.firstElementChild as HTMLElement).classList.add('doodle-sketchy');
                g.appendChild(fo);
                g.appendChild(this.sketchPath(sk.rectOutline(d.x, d.y, d.w, d.h), { ...s, color: '#4a5568', width: 1.5, opacity: 0.9 }));
                g.setAttribute('transform', `rotate(${tilt} ${d.x + d.w / 2} ${d.y + d.h / 2})`);
                break;
            }
            default:
                return null;
        }
        return g;
    }

    private outline(el: SVGElement, s: Style) {
        el.setAttribute('stroke', s.color);
        el.setAttribute('stroke-width', String(s.width));
        el.setAttribute('fill', 'none');
        el.setAttribute('vector-effect', 'non-scaling-stroke');
        el.setAttribute('stroke-linecap', 'round');
        el.setAttribute('stroke-linejoin', 'round');
        if (s.dashed) el.setAttribute('stroke-dasharray', `${s.width * 3} ${s.width * 2}`);
        el.setAttribute('opacity', String(s.opacity));
    }

    private ellipse(cx: number, cy: number, rx: number, ry: number, s: Style) {
        const e = document.createElementNS(SVG, 'ellipse');
        e.setAttribute('cx', String(cx)); e.setAttribute('cy', String(cy));
        e.setAttribute('rx', String(Math.max(1, rx))); e.setAttribute('ry', String(Math.max(1, ry)));
        this.outline(e, s);
        return e;
    }

    private rect(x: number, y: number, w: number, h: number, s: Style) {
        const r = document.createElementNS(SVG, 'rect');
        r.setAttribute('x', String(x)); r.setAttribute('y', String(y));
        r.setAttribute('width', String(Math.max(1, w))); r.setAttribute('height', String(Math.max(1, h)));
        r.setAttribute('fill', s.color);
        r.setAttribute('fill-opacity', String(s.opacity));
        if (s.width > 0) {
            r.setAttribute('stroke', s.color);
            r.setAttribute('stroke-width', String(s.width));
            r.setAttribute('vector-effect', 'non-scaling-stroke');
        } else {
            r.setAttribute('stroke', 'none');
        }
        return r;
    }

    private arrow(x1: number, y1: number, x2: number, y2: number, s: Style, bends = 0,
                  startEnd: ArrowEnd = 'NONE', endEnd: ArrowEnd = 'ARROW', sk?: Sketch) {
        const g = document.createElementNS(SVG, 'g');
        g.setAttribute('opacity', String(s.opacity));
        const ink = { ...s, opacity: 1 };
        const curved = bends === 1 || bends === 2;
        const [c1, c2] = arrowControls(x1, y1, x2, y2, bends);
        const line = document.createElementNS(SVG, sk || curved ? 'path' : 'line');
        if (sk) {
            // Fixed endpoints prevent rough strokes from poking beyond end decorations.
            line.setAttribute('d', curved ? sk.curve(x1, y1, x2, y2, c1, c2)
                : sk.curve(x1, y1, x2, y2,
                    { x: x1 + (x2 - x1) / 3, y: y1 + (y2 - y1) / 3 },
                    { x: x1 + (x2 - x1) * 2 / 3, y: y1 + (y2 - y1) * 2 / 3 }));
        } else if (curved) {
            line.setAttribute('d', curvePath(x1, y1, x2, y2, c1, c2));
        } else {
            line.setAttribute('x1', String(x1)); line.setAttribute('y1', String(y1));
            line.setAttribute('x2', String(x2)); line.setAttribute('y2', String(y2));
        }
        this.outline(line, ink);
        g.appendChild(line);
        const size = sk ? Math.min(24, Math.max(10, Math.hypot(x2 - x1, y2 - y1) * 0.15)) : Math.max(10, s.width * 5);
        // Only the shaft is masked, keeping hollow ends transparent over any workspace content.
        const mask = document.createElementNS(SVG, 'mask');
        mask.id = `doodle-arrow-mask-${++nextArrowMaskId}`;
        mask.setAttribute('maskUnits', 'userSpaceOnUse');
        mask.setAttribute('maskContentUnits', 'userSpaceOnUse');
        mask.style.maskType = 'luminance';
        const pad = size + s.width + 10;
        const bounds = {
            x: Math.min(x1, x2, c1.x, c2.x) - pad, y: Math.min(y1, y2, c1.y, c2.y) - pad,
            width: Math.max(x1, x2, c1.x, c2.x) - Math.min(x1, x2, c1.x, c2.x) + pad * 2,
            height: Math.max(y1, y2, c1.y, c2.y) - Math.min(y1, y2, c1.y, c2.y) + pad * 2,
        };
        const background = document.createElementNS(SVG, 'rect');
        for (const [key, value] of Object.entries(bounds)) {
            mask.setAttribute(key, String(value));
            background.setAttribute(key, String(value));
        }
        background.setAttribute('fill', 'white');
        mask.appendChild(background);
        const ends: [ArrowEnd, number, number, number][] = [
            [arrowEnd(startEnd, 'NONE'), x1, y1, Math.atan2(y1 - (curved ? c1.y : y2), x1 - (curved ? c1.x : x2))],
            [arrowEnd(endEnd, 'ARROW'), x2, y2, Math.atan2(y2 - (curved ? c2.y : y1), x2 - (curved ? c2.x : x1))],
        ];
        for (const [index, [kind, x, y, angle]] of ends.entries()) {
            if (kind === 'NONE') continue;
            const decoration = this.arrowDecoration(kind, size, ink, sk);
            const transform = `translate(${x} ${y}) rotate(${angle * 180 / Math.PI})`;
            decoration.el.setAttribute('transform', transform);
            decoration.el.setAttribute('data-arrow-end', index === 0 ? 'start' : 'end');
            decoration.el.setAttribute('data-arrow-end-kind', kind);
            g.appendChild(decoration.el);
            if (decoration.silhouette) {
                decoration.silhouette.setAttribute('transform', transform);
                mask.appendChild(decoration.silhouette);
            }
        }
        if (mask.childElementCount > 1) {
            const defs = document.createElementNS(SVG, 'defs');
            defs.appendChild(mask);
            g.appendChild(defs);
            line.setAttribute('mask', `url(#${mask.id})`);
        }
        return g;
    }

    /** End geometry points out along +x, with its tip exactly at the anchor. */
    private arrowDecoration(kind: ArrowEnd, size: number, s: Style, sk?: Sketch) {
        const el = document.createElementNS(SVG, 'g');
        const openArrow = kind === 'ARROW' && !!sk;
        const circle = kind.startsWith('CIRCLE');
        const diamond = kind.startsWith('DIAMOND');
        const filled = kind.endsWith('_FILLED') || (kind === 'ARROW' && !sk);
        const pts: [number, number][] = diamond
            ? [[0, 0], [-size / 2, size / 2], [-size, 0], [-size / 2, -size / 2]]
            : [[0, 0], [-size, size / 2], [-size, -size / 2]];
        const r = size / 2;
        const d = circle ? `M 0 0 A ${r} ${r} 0 1 0 ${-size} 0 A ${r} ${r} 0 1 0 0 0 Z`
            : `M ${pts.map(p => p.join(' ')).join(' L ')} Z`;
        let silhouette: SVGPathElement | undefined;
        if (!openArrow) {
            silhouette = document.createElementNS(SVG, 'path');
            silhouette.setAttribute('d', d);
            silhouette.setAttribute('fill', 'black');
            if (sk || kind !== 'ARROW') {
                silhouette.setAttribute('stroke', 'black');
                silhouette.setAttribute('stroke-width', String(s.width));
                silhouette.setAttribute('vector-effect', 'non-scaling-stroke');
            }
        }
        if (filled) {
            const fill = document.createElementNS(SVG, 'path');
            fill.setAttribute('d', d);
            fill.setAttribute('fill', s.color);
            el.appendChild(fill);
        }
        // End outlines stay solid even when the connecting shaft is dashed.
        const solid = { ...s, dashed: false };
        if (sk) {
            const outline = openArrow
                ? [-1, 1].map(side => sk.line(0, 0, -Math.cos(0.45) * size, side * Math.sin(0.45) * size)).join(' ')
                : circle ? sk.ellipse(-r, 0, r, r)
                : pts.map((p, i) => { const q = pts[(i + 1) % pts.length]; return sk.line(p[0], p[1], q[0], q[1]); }).join(' ');
            el.appendChild(this.sketchPath(outline, solid));
        } else if (kind !== 'ARROW') {
            el.appendChild(this.sketchPath(d, solid));
        }
        return { el, silhouette };
    }

    private path(points: Pt[], s: Style, marker: boolean) {
        const p = document.createElementNS(SVG, 'path');
        p.setAttribute('d', points.length ? 'M ' + points.map((q) => `${q.x} ${q.y}`).join(' L ') : 'M 0 0');
        if (marker) {
            // Marker width is in surface units so it keeps covering the same content under zoom.
            p.setAttribute('stroke', s.color);
            p.setAttribute('stroke-width', String(s.width));
            p.setAttribute('fill', 'none');
            p.setAttribute('stroke-linecap', 'round');
            p.setAttribute('stroke-linejoin', 'round');
            p.setAttribute('opacity', String(s.opacity));
        } else {
            this.outline(p, s);
        }
        return p;
    }

    private foreign(x: number, y: number, w: number, h: number) {
        const fo = document.createElementNS(SVG, 'foreignObject');
        fo.setAttribute('x', String(x)); fo.setAttribute('y', String(y));
        fo.setAttribute('width', String(w)); fo.setAttribute('height', String(h));
        fo.style.overflow = 'visible';
        return fo;
    }

    private text(x: number, y: number, text: string, s: Style) {
        const fo = this.foreign(x, y, 1, 1);
        const div = document.createElementNS(XHTML, 'div') as HTMLDivElement;
        div.className = 'doodle-text';
        div.textContent = text ?? '';
        div.style.color = s.color;
        div.style.opacity = String(s.opacity);
        fo.appendChild(div);
        return fo;
    }

    private note(x: number, y: number, w: number, h: number, text: string, s: Style) {
        const fo = this.foreign(x, y, w, h);
        const div = document.createElementNS(XHTML, 'div') as HTMLDivElement;
        div.className = 'doodle-note';
        div.textContent = text ?? '';
        div.style.background = s.color;
        div.style.opacity = String(s.opacity);
        fo.appendChild(div);
        return fo;
    }

    // ── Tools ────────────────────────────────────────────────────────────

    private applyTool() {
        const drawing = this._tool !== 'NONE' && this._tool !== 'ERASER' && this._tool !== 'TEXT_MARKER';
        // Drawing tools take the whole surface through the catcher; otherwise only the shapes are hit-testable.
        this.svg.style.pointerEvents = 'none';
        this.catcher.style.pointerEvents = drawing ? 'all' : 'none';
        this.catcher.style.cursor = drawing ? 'crosshair' : '';
        this.shapes.style.pointerEvents = this._tool === 'ERASER' || this._tool === 'NONE' ? 'auto' : 'none';
        this.closeEditor(false);
        const vp = this.viewport();
        if (!vp) return;
        if (this._tool === 'NONE') this.restoreDragging();
        else if (this.savedDragging === undefined) {
            this.savedDragging = vp.draggingEnabled;
            vp.draggingEnabled = false;
        }
    }

    private restoreDragging() {
        const vp = this.viewport();
        if (vp && this.savedDragging !== undefined) vp.draggingEnabled = this.savedDragging;
        this.savedDragging = undefined;
    }

    private setToolFromClient(t: Tool) {
        if (t === this._tool) return;
        this._tool = t;
        this.applyTool();
        this.dispatchEvent(new CustomEvent('doodle-tool-changed', { bubbles: true, composed: true, detail: { tool: t } }));
    }

    private emit(detail: any) {
        detail.style = this._drawStyle;
        this.dispatchEvent(new CustomEvent('doodle-drawn', { bubbles: true, composed: true, detail }));
    }

    /** Surface point for a pointer event, with the id of the item under the pointer. */
    private pointAt(e: { clientX: number; clientY: number }): Pt {
        const vp = this.viewport();
        const p = vp ? vp.toWorkspace(e.clientX, e.clientY) : { x: e.clientX, y: e.clientY };
        const prev = this.catcher.style.pointerEvents;
        this.catcher.style.pointerEvents = 'none';
        const prevShapes = this.shapes.style.pointerEvents;
        this.shapes.style.pointerEvents = 'none';
        const under = document.elementFromPoint(e.clientX, e.clientY);
        this.catcher.style.pointerEvents = prev;
        this.shapes.style.pointerEvents = prevShapes;
        const item = under?.closest?.('[data-item]') as HTMLElement | null;
        const itemId = item?.dataset.itemId ? parseInt(item.dataset.itemId, 10) : undefined;
        return itemId !== undefined ? { ...p, itemId } : p;
    }

    private onPointerDown = (e: PointerEvent) => {
        if (e.button !== 0) return;
        const t = this._tool;
        if (t === 'NONE' || t === 'ERASER' || t === 'TEXT_MARKER') return;
        e.stopPropagation();
        e.preventDefault();
        if (t === 'TEXT' || t === 'NOTE') { this.openEditor(this.pointAt(e), t); return; }
        this.svg.setPointerCapture(e.pointerId);
        const start = this.pointAt(e);
        this.drawing = { start, points: [start] };
    };

    private onPointerMove = (e: PointerEvent) => {
        if (!this.drawing) return;
        e.stopPropagation();
        const p = this.pointAt(e);
        const d = this.drawing;
        const s = this._drawStyle;
        this.preview.replaceChildren();
        switch (this._tool) {
            case 'ELLIPSE': {
                const cx = (d.start.x + p.x) / 2, cy = (d.start.y + p.y) / 2;
                this.preview.appendChild(this.ellipse(cx, cy, Math.abs(p.x - d.start.x) / 2, Math.abs(p.y - d.start.y) / 2, s));
                break;
            }
            case 'RECT':
                this.preview.appendChild(this.rect(Math.min(d.start.x, p.x), Math.min(d.start.y, p.y),
                    Math.abs(p.x - d.start.x), Math.abs(p.y - d.start.y), s));
                break;
            case 'ARROW':
                this.preview.appendChild(this.arrow(d.start.x, d.start.y, p.x, p.y, s, this._arrowBends, this._arrowStart, this._arrowEnd));
                break;
            case 'PEN':
            case 'MARKER': {
                const last = d.points[d.points.length - 1];
                const scale = this.viewport()?.currentScale ?? 1;
                if (Math.hypot(p.x - last.x, p.y - last.y) * scale >= 2) d.points.push(p);
                this.preview.appendChild(this.path(d.points, s, this._tool === 'MARKER'));
                break;
            }
        }
        d.points[d.points.length - 1] = this._tool === 'PEN' || this._tool === 'MARKER' ? d.points[d.points.length - 1] : p;
        if (this._tool !== 'PEN' && this._tool !== 'MARKER') d.points = [d.start, p];
    };

    private onPointerUp = (e: PointerEvent) => {
        if (!this.drawing) return;
        e.stopPropagation();
        const d = this.drawing;
        this.drawing = undefined;
        try { this.svg.releasePointerCapture(e.pointerId); } catch { /* already released */ }
        const end = d.points.length > 1 ? d.points[d.points.length - 1] : d.start;
        const tiny = Math.hypot(end.x - d.start.x, end.y - d.start.y) * (this.viewport()?.currentScale ?? 1) < 3;
        switch (this._tool) {
            case 'ELLIPSE':
                if (tiny) break;
                this.emit({ type: 'ellipse', center: this.mid(d.start, end), rx: Math.abs(end.x - d.start.x) / 2, ry: Math.abs(end.y - d.start.y) / 2 });
                break;
            case 'RECT':
                if (tiny) break;
                this.emit({ type: 'rect', from: d.start, to: end });
                break;
            case 'ARROW':
                if (tiny) break;
                this.emit({ type: 'arrow', from: d.start, to: end, bends: this._arrowBends, startEnd: this._arrowStart, endEnd: this._arrowEnd });
                break;
            case 'PEN':
                if (d.points.length > 1) this.emit({ type: 'stroke', points: d.points });
                break;
            case 'MARKER':
                if (d.points.length > 1) this.emit({ type: 'marker', points: d.points });
                break;
        }
        if (tiny || (this._tool !== 'PEN' && this._tool !== 'MARKER' ? false : d.points.length <= 1)) this.preview.replaceChildren();
    };

    private mid(a: Pt, b: Pt): Pt {
        const m: Pt = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
        if (a.itemId !== undefined && a.itemId === b.itemId) m.itemId = a.itemId;
        return m;
    }

    private onShapeClick = (e: MouseEvent) => {
        const target = (e.target as Element).closest('[data-doodle-id]') as SVGElement | null;
        if (!target) return;
        const id = target.getAttribute('data-doodle-id')!;
        e.stopPropagation();
        if (this._tool === 'ERASER') {
            this.dispatchEvent(new CustomEvent('doodle-erased', { bubbles: true, composed: true, detail: { id } }));
        } else if (this._tool === 'NONE') {
            this.dispatchEvent(new CustomEvent('doodle-clicked', { bubbles: true, composed: true, detail: { id } }));
        }
    };

    /** Text marker: on mouse release, turn the current DOM selection inside an item into rectangles. */
    private onDocumentMouseUp = () => {
        if (this._tool !== 'TEXT_MARKER') return;
        const sel = document.getSelection();
        if (!sel || sel.isCollapsed || sel.rangeCount === 0) return;
        const range = sel.getRangeAt(0);
        const node = range.commonAncestorContainer;
        const el = node.nodeType === Node.ELEMENT_NODE ? (node as Element) : node.parentElement;
        const item = el?.closest('[data-item]') as HTMLElement | null;
        const vp = this.viewport();
        if (!item || !vp || !item.dataset.itemId) return;
        const itemRect = item.getBoundingClientRect();
        const w = parseFloat(item.dataset.w ?? '0'), h = parseFloat(item.dataset.h ?? '0');
        const sx = w > 0 ? itemRect.width / w : vp.currentScale;
        const sy = h > 0 ? itemRect.height / h : vp.currentScale;
        const rects = Array.from(range.getClientRects())
            .filter((r) => r.width > 0 && r.height > 0)
            .map((r) => ({ x: (r.left - itemRect.left) / sx, y: (r.top - itemRect.top) / sy, w: r.width / sx, h: r.height / sy }));
        if (rects.length === 0) return;
        sel.removeAllRanges();
        this.emit({ type: 'textHighlight', itemId: parseInt(item.dataset.itemId, 10), rects });
    };

    // ── Inline editor for text and notes ─────────────────────────────────

    private openEditor(at: Pt, kind: 'TEXT' | 'NOTE') {
        this.closeEditor(false);
        const isNote = kind === 'NOTE';
        const w = isNote ? 200 : 1, h = isNote ? 120 : 1;
        const fo = this.foreign(at.x, at.y, w, h);
        fo.style.pointerEvents = 'all';
        const div = document.createElementNS(XHTML, 'div') as HTMLDivElement;
        div.className = 'doodle-editor' + (isNote ? ' doodle-note' : ' doodle-text');
        div.contentEditable = 'true';
        if (isNote) div.style.background = this._drawStyle.color;
        else div.style.color = this._drawStyle.color;
        div.addEventListener('keydown', (e) => {
            e.stopPropagation();
            if (e.key === 'Escape') { this.closeEditor(false); }
            else if (e.key === 'Enter' && (!isNote || e.ctrlKey || e.metaKey)) { e.preventDefault(); this.closeEditor(true); }
        });
        div.addEventListener('pointerdown', (e) => e.stopPropagation());
        div.addEventListener('blur', () => this.closeEditor(true));
        fo.appendChild(div);
        this.preview.appendChild(fo);
        this.preview.style.pointerEvents = 'all';
        this.editor = fo;
        (fo as any)._kind = kind;
        (fo as any)._at = at;
        requestAnimationFrame(() => div.focus());
    }

    private closeEditor(commit: boolean) {
        const fo = this.editor;
        if (!fo) return;
        this.editor = undefined;
        this.preview.style.pointerEvents = 'none';
        const div = fo.firstElementChild as HTMLDivElement;
        const text = (div.innerText ?? '').trim();
        const kind = (fo as any)._kind as 'TEXT' | 'NOTE';
        const at = (fo as any)._at as Pt;
        fo.remove();
        if (!commit || !text) return;
        if (kind === 'NOTE') this.emit({ type: 'note', at, w: 200, h: 120, text });
        else this.emit({ type: 'text', at, text });
    }

    private onKeyDown = (e: KeyboardEvent) => {
        if (e.key === 'Escape' && this._tool !== 'NONE' && !this.editor) {
            this.setToolFromClient('NONE');
        }
    };
}

customElements.define('workspace-doodle-layer', WorkspaceDoodleLayer);
