package org.adaway.ui.prefs

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import org.adaway.helper.ThemeHelper

/**
 * This activity is the preferences activity.
 */
class PrefsActivity : AppCompatActivity() {
    private var settingsContainerId: Int = View.NO_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)
        settingsContainerId = savedInstanceState?.getInt(SETTINGS_CONTAINER_ID_KEY) ?: View.generateViewId()

        setContentView(
            FragmentContainerView(this).apply {
                id = settingsContainerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        )

        if (savedInstanceState == null) {
            window.decorView.post {
                if (supportFragmentManager.findFragmentById(settingsContainerId) == null) {
                    supportFragmentManager.beginTransaction()
                        .replace(settingsContainerId, PrefsMainFragment())
                        .commit()
                }
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SETTINGS_CONTAINER_ID_KEY, settingsContainerId)
        super.onSaveInstanceState(outState)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onSupportNavigateUp()
        }
        return true
    }

    companion object {
        private const val SETTINGS_CONTAINER_ID_KEY = "settings_container_id"
    }
}
