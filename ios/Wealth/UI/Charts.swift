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
struct LineChart: View {
    let values: [Double]

    var body: some View {
        let up = values.count < 2 || (values.last ?? 0) >= (values.first ?? 0)
        let colour: Color = up ? .gain : .loss
        GeometryReader { geo in
            let size = geo.size
            if values.count > 1 {
                let points = points(in: size)
                ZStack(alignment: .topLeading) {
                    // The same line closed downwards, filled with a fading wash.
                    Path { p in
                        p.move(to: CGPoint(x: 0, y: size.height))
                        points.forEach { p.addLine(to: $0) }
                        p.addLine(to: CGPoint(x: size.width, y: size.height))
                        p.closeSubpath()
                    }
                    .fill(LinearGradient(colors: [colour.opacity(0.28), .clear],
                                         startPoint: .top, endPoint: .bottom))
                    Path { p in p.addLines(points) }
                        .stroke(colour, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                    if let last = points.last {
                        Circle().fill(colour)
                            .frame(width: 8, height: 8)
                            .position(last)
                    }
                    Rectangle().fill(Color.secondary.opacity(0.18))
                        .frame(width: size.width, height: 1)
                        .offset(y: size.height - 1)
                }
            }
        }
        .accessibilityHidden(true)
    }

    private func points(in size: CGSize) -> [CGPoint] {
        let lo = values.min() ?? 0, hi = values.max() ?? 0
        let span = hi > lo ? hi - lo : 1
        let step = size.width / CGFloat(values.count - 1)
        // A little air above and below, so the line never touches the edge.
        let top = size.height * 0.08, usable = size.height * 0.84
        return values.enumerated().map { i, v in
            CGPoint(x: step * CGFloat(i), y: top + usable * (1 - CGFloat((v - lo) / span)))
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
