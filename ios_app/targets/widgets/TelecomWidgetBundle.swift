import WidgetKit
import SwiftUI

@main
struct TelecomWidgetBundle: WidgetBundle {
    var body: some Widget {
        TelecomLiveActivityWidget()
        TelecomHomeScreenWidget()
    }
}
