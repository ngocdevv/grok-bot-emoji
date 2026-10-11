import SwiftUI

/// Mirrors `src/app/index.tsx` + `src/app/_layout.tsx`: a dark, header-less scroll view
/// hosting the Orb and its expression picker.
struct ContentView: View {
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            GrokOrbExpressionPicker()
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 36)
        }
        .background(GrokOrbPalette.screenBackground.ignoresSafeArea())
        .statusBarHidden(true)
        .preferredColorScheme(.dark)
    }
}

#Preview {
    ContentView()
}
