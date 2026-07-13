package com.kazisasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kazisasa.app.ui.navigation.KaziSasaNavHost
import com.kazisasa.app.ui.theme.KaziSasaAppTheme

/**
 * No notification-permission request here on purpose (recommendations doc §16:
 * "requesting notification permission immediately on launch may reduce
 * acceptance... move it into the reminder flow"). The request now happens
 * inline in OpportunityDetailScreen when the user actually taps "Remind me",
 * via rememberLauncherForActivityResult - by then the person already
 * understands what they're granting permission for.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openOpportunityId = intent?.getStringExtra(EXTRA_OPEN_OPPORTUNITY_ID)
        val openProfileId = intent?.getStringExtra(EXTRA_OPEN_PROFILE_ID)

        setContent {
            KaziSasaAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KaziSasaNavHost(startOpportunityId = openOpportunityId, startProfileId = openProfileId)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_OPPORTUNITY_ID = "open_opportunity_id"
        const val EXTRA_OPEN_PROFILE_ID = "open_profile_id"
    }
}
