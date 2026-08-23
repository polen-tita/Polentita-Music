package com.polentita.music.core.localization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.polentita.music.BuildConfig
import com.polentita.music.R
import java.util.Locale

/**
 * Languages exposed by the in-app selector. The language is explicit rather than following the
 * device locale so each distribution can keep its advertised language on a fresh installation.
 */
enum class AppLanguage(
    val tag: String,
    @DrawableRes val flagRes: Int,
    @StringRes val accessibilityRes: Int,
) {
    SPANISH("es-ES", R.drawable.flag_spain, R.string.language_spanish_accessibility),
    ENGLISH("en-US", R.drawable.flag_united_states, R.string.language_english_accessibility),
    PORTUGUESE("pt-BR", R.drawable.flag_brazil, R.string.language_portuguese_accessibility),
    FRENCH("fr-FR", R.drawable.flag_france, R.string.language_french_accessibility),
    CHINESE("zh-CN", R.drawable.flag_china, R.string.language_chinese_accessibility),
    ;

    val locale: Locale
        get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(value: String?, fallback: AppLanguage = SPANISH): AppLanguage =
            entries.firstOrNull { it.tag == value } ?: fallback

        val defaultForBuild: AppLanguage
            get() = fromTag(BuildConfig.DEFAULT_APP_LANGUAGE)
    }
}

/**
 * A context wrapper that keeps Activity operations delegated to the original context while
 * exposing resources resolved for the selected language.
 */
class AppLanguageContext(
    base: Context,
    language: AppLanguage,
) : ContextWrapper(base) {
    private val localizedContext = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply {
            setLocale(language.locale)
        },
    )

    override fun getResources(): Resources = localizedContext.resources

    override fun getAssets() = localizedContext.assets
}

fun Context.withAppLanguage(language: AppLanguage): Context = AppLanguageContext(this, language)
