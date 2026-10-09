package il.co.kiosk;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** App-local language; never changes the device language or stored user content. */
final class Language {
    static String code(Context context) {
        String saved = context.getSharedPreferences("kiosk", Context.MODE_PRIVATE).getString("language", "he");
        return "en".equals(saved) ? "en" : "he";
    }
    static Context wrap(Context base) {
        Locale locale = new Locale(code(base));
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return base.createConfigurationContext(configuration);
    }
}
