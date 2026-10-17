package dev.retui.privatespacetest;
public class MainActivity extends android.app.Activity {
 public void onCreate(android.os.Bundle state) {
  super.onCreate(state);
  android.widget.TextView label = new android.widget.TextView(this);
  label.setText("PRIVATE PROFILE TEST"); label.setTextSize(24); label.setGravity(17);
  setContentView(label);
  android.util.Log.i("PrivateSpaceFixture", "Started " + android.os.Process.myUserHandle());
 }
}
