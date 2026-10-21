package app.goallive.live;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onPause() {
        super.onPause();
        // the user may have just picked "my team" or the score moved on: let the home-screen widget catch up
        ScoreWidget.requestUpdate(this);
    }
}
