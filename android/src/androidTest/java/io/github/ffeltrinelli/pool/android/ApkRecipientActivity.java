package io.github.ffeltrinelli.pool.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.InputStream;
import java.security.MessageDigest;

/** Independent test-APK UID: no dependency on the target app's Kotlin runtime. */
public class ApkRecipientActivity extends Activity {
    public static final String RESULT = "io.github.ffeltrinelli.pool.APK_TEST_READ";

    @Override @SuppressWarnings("deprecation")
    public void onCreate(Bundle state) {
        super.onCreate(state);
        byte[] digest = new byte[0];
        String failure = null;
        try {
            Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) sha.update(buffer, 0, count);
                digest = sha.digest();
            }
        } catch (Exception error) {
            failure = error.toString();
        }
        sendBroadcast(new Intent(RESULT).setPackage("io.github.ffeltrinelli.pool")
            .putExtra("digest", digest).putExtra("failure", failure));
        finish();
    }
}
