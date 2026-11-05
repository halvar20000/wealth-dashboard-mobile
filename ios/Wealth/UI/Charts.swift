import SwiftUI

// Two charts, drawn by hand, as on Android.
//
// A charting framework brings its own axes, legends and theme for a line
// and a ring; a `Path` draws both in a few lines and inherits the app's
// colours. No axes, no gridlines, no legend inside the picture: the
// numbers that matter are written next to it as text, where VoiceOver
// can find them.

/// The eight colours a share is drawn in, in order — the same as Android's.
let slices: [Color] = [0x4F86F7, 0x34D399, 0xF59E0B, 0xF87171,
                       0xA78BFA, 0x22D3EE, 0xFB7185, 0x94A3B8].map { rgb in
    Color(red: Double((rgb >> 16) & 0xFF) / 255,
          green: Double((rgb >> 8) & 0xFF) / 255,
          blue: Double(rgb & 0xFF) / 255)
}

/// The line of a value over time. Rising or falling decides the colour,
/// because the first thing anybody asks of this chart is the direction.
///
/// A short press on the line picks the nearest day: a thin rule marks it
/// and a label above says when and how much. Sliding then walks through
/// time; lifting the finger puts the chart back as it was. The press
/// comes first so that a swipe over the chart still scrolls the list.
///
/// Two fingers zoom into time, and only time: spreading them shows fewer
/// days across the width, around the place they started. The height
/// follows the days in view, so a quiet week is not a flat line. A
/// button in the corner puts the whole window back, and so does new data.
struct LineChart: View {
    let values: [Double]
    /// One ISO date per value, for the label; without them the chart
    /// still draws but a press shows nothing.
    var dates: [String] = []
    var currency: String? = nil

    @State private var picked: Int?
    @State private var labelWidth: CGFloat = 0
    /// The window in view, in fractional indices; nil is all of it.
    @State private var window: ClosedRange<Double>?
    /// The window as the pinch found it, while two fingers are down.
    @State private var pinchStart: ClosedRange<Double>?

    private var scrubbable: Bool { values.count > 1 && dates.count == values.count }
    private var last: Double { Double(max(values.count - 1, 1)) }
    private var inView: ClosedRange<Double> { window ?? 0...last }
    /// The first and last index drawn: every point in view, and the one
    /// just outside each edge so the line runs off the side.
    private var drawn: ClosedRange<Int> {
        let lo = max(Int(inView.lowerBound.rounded(.down)), 0)
        let hi = min(Int(inView.upperBound.rounded(.up)), values.count - 1)
        return lo...max(hi, lo)
    }

    var body: some View {
        let up = values.count < 2 || (values.last ?? 0) >= (values.first ?? 0)
        let colour: Color = up ? .gain : .loss
        GeometryReader { geo in
            let size = geo.size
            if values.count > 1 {
                let points = points(in: size)
                let range = drawn
                let firstX = points[range.lowerBound].x, lastX = points[range.upperBound].x
                ZStack(alignment: .topLeading) {
                    Group {
                        // The same line closed downwards, filled with a fading wash.
                        Path { p in
                            p.move(to: CGPoint(x: firstX, y: size.height))
                            range.forEach { p.addLine(to: points[$0]) }
                            p.addLine(to: CGPoint(x: lastX, y: size.height))
                            p.closeSubpath()
                        }
                        .fill(LinearGradient(colors: [colour.opacity(0.28), .clear],
                                             startPoint: .top, endPoint: .bottom))
                        Path { p in p.addLines(range.map { points[$0] }) }
                            .stroke(colour, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                    }
                    .clipShape(Rectangle())
                    if let i = picked, points.indices.contains(i) {
                        Rectangle().fill(colour.opacity(0.5))
                            .frame(width: 1, height: size.height)
                            .offset(x: points[i].x - 0.5)
                        Circle().fill(colour)
                            .frame(width: 10, height: 10)
                            .position(points[i])
                    } else if range.upperBound == values.count - 1, let end = points.last {
                        Circle().fill(colour)
                            .frame(width: 8, height: 8)
                            .position(end)
                    }
                    Rectangle().fill(Color.secondary.opacity(0.18))
                        .frame(width: size.width, height: 1)
                        .offset(y: size.height - 1)
                    if let i = picked, points.indices.contains(i) {
                        label(i)
                            .offset(x: min(max(points[i].x - labelWidth / 2, 0),
                                           max(size.width - labelWidth, 0)))
                    } else if window != nil {
                        zoomBar.frame(maxWidth: .infinity, alignment: .trailing)
                    }
                }
                .contentShape(Rectangle())
                .gesture(scrub(width: size.width), including: scrubbable ? .all : .subviews)
                .simultaneousGesture(zoom)
            }
        }
        .onChange(of: values) { window = nil; pinchStart = nil }
        .accessibilityHidden(true)
    }

    /// The days in view, and the way back to all of them.
    private var zoomBar: some View {
        HStack(spacing: 6) {
            if scrubbable {
                let a = dates[min(max(Int(inView.lowerBound.rounded()), 0), dates.count - 1)]
                let b = dates[min(max(Int(inView.upperBound.rounded()), 0), dates.count - 1)]
                Text(verbatim: "\(Fmt.day(a)) – \(Fmt.day(b))")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            Button("Show all") { withAnimation(.snappy) { window = nil } }
                .font(.caption2.weight(.semibold))
                .buttonStyle(.borderless)
        }
        .padding(.horizontal, 8).padding(.vertical, 4)
        .background(.regularMaterial, in: Capsule())
    }

    /// Two fingers: the day under where they started stays put, and the
    /// window narrows or widens around it.
    private var zoom: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                guard values.count > 2 else { return }
                picked = nil
                let start = pinchStart ?? inView
                if pinchStart == nil { pinchStart = start }
                let w = start.upperBound - start.lowerBound
                let narrowest = min(last, 3)
                let newW = min(max(w / max(value.magnification, 0.01), narrowest), last)
                let f = Double(min(max(value.startAnchor.x, 0), 1))
                let held = start.lowerBound + f * w
                let from = min(max(held - f * newW, 0), last - newW)
                window = newW >= last - 0.001 ? nil : from...(from + newW)
            }
            .onEnded { _ in pinchStart = nil }
    }

    /// The label rides above the finger but stays inside the chart, so
    /// the first and last days are readable too.
    private func label(_ i: Int) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(Fmt.day(dates[i])).font(.caption2).foregroundStyle(.secondary)
            Text(Fmt.money(values[i], currency)).font(.subheadline.weight(.semibold))
        }
        .padding(.horizontal, 10).padding(.vertical, 6)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
        .fixedSize()
        .background(GeometryReader { g in
            Color.clear.onAppear { labelWidth = g.size.width }
                .onChange(of: g.size.width) { _, w in labelWidth = w }
        })
    }

    private func scrub(width: CGFloat) -> some Gesture {
        LongPressGesture(minimumDuration: 0.15)
            .sequenced(before: DragGesture(minimumDistance: 0))
            .onChanged { value in
                switch value {
                case .first(true):
                    // The press is recognised before the finger moves;
                    // the drag's first event says where it is.
                    break
                case .second(true, let drag?):
                    guard pinchStart == nil else { break }
                    let i = index(at: drag.location.x, width: width)
                    if i != picked {
                        picked = i
                        UISelectionFeedbackGenerator().selectionChanged()
                    }
                default:
                    break
                }
            }
            .onEnded { _ in picked = nil }
    }

    private func index(at x: CGFloat, width: CGFloat) -> Int {
        guard width > 0, values.count > 1 else { return 0 }
        let v = inView
        let i = Int((v.lowerBound + Double(x / width) * (v.upperBound - v.lowerBound)).rounded())
        return min(max(i, drawn.lowerBound), drawn.upperBound)
    }

    private func points(in size: CGSize) -> [CGPoint] {
        // The height follows the days in view.
        let shown = values[drawn]
        let lo = shown.min() ?? 0, hi = shown.max() ?? 0
        let span = hi > lo ? hi - lo : 1
        let v = inView
        let width = max(v.upperBound - v.lowerBound, 0.001)
        // A little air above and below, so the line never touches the edge.
        let top = size.height * 0.08, usable = size.height * 0.84
        return values.enumerated().map { i, value in
            CGPoint(x: size.width * CGFloat((Double(i) - v.lowerBound) / width),
                    y: top + usable * (1 - CGFloat((value - lo) / span)))
        }
    }
}

/// The ring: one arc per share, biggest first, in the order the colours
/// are listed so that the legend beside it can use the same index.
struct DonutChart<Centre: View>: View {
    let shares: [Double]
    var width: CGFloat = 18
    @ViewBuilder var centre: () -> Centre

    var body: some View {
        let total = max(shares.reduce(0, +), .leastNonzeroMagnitude)
        ZStack {
            Circle().stroke(Color.secondary.opacity(0.12), lineWidth: width)
            ForEach(Array(shares.enumerated()), id: \.offset) { i, share in
                let start = shares.prefix(i).reduce(0, +) / total
                let end = start + share / total
                // A hair's gap between slices, unless there is only one.
                let gap = shares.count > 1 ? min(0.004, (end - start) / 2) : 0
                Circle()
                    .trim(from: start, to: max(start, end - gap))
                    .stroke(slices[i % slices.count], style: StrokeStyle(lineWidth: width, lineCap: .butt))
                    .rotationEffect(.degrees(-90))
            }
            centre()
        }
        .padding(width / 2)
    }
}
