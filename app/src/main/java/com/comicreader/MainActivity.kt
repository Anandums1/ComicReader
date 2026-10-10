package com.comicreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.domain.model.AppTheme
import com.comicreader.presentation.navigation.ComicReaderNavGraph
import com.comicreader.ui.theme.ComicReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request All Files Access permission
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }

        setContent {
            val currentTheme by userPreferencesDataStore.themeFlow.collectAsState(initial = AppTheme.DARK)
            ComicReaderTheme(theme = currentTheme) {
                ComicReaderNavGraph()
            }
        }
    }
}
