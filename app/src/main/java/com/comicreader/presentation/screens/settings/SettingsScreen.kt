package com.comicreader.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Environment
import com.comicreader.domain.model.AppTheme
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFolderDialog by remember { mutableStateOf(false) }
    var folderText by remember { mutableStateOf("") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val path = resolveUriToPath(it)
            if (path != null) {
                viewModel.setWatchedDirectory(path)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Section 1: Appearance & UI Themes
            SectionHeader(title = "Appearance & Themes", icon = Icons.Default.ColorLens)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    ThemeOptionRow(
                        title = "Dark AMOLED",
                        subtitle = "Pure black #000000 with violet & cyber cyan accents",
                        selected = uiState.theme == AppTheme.DARK,
                        onSelect = { viewModel.setTheme(AppTheme.DARK) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ThemeOptionRow(
                        title = "Light Theme",
                        subtitle = "Paper white #FAFAFA with cobalt blue & pink accents",
                        selected = uiState.theme == AppTheme.LIGHT,
                        onSelect = { viewModel.setTheme(AppTheme.LIGHT) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ThemeOptionRow(
                        title = "Material You Adaptive",
                        subtitle = "Dynamic color palette extracted from system wallpaper",
                        selected = uiState.theme == AppTheme.ADAPTIVE,
                        onSelect = { viewModel.setTheme(AppTheme.ADAPTIVE) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ThemeOptionRow(
                        title = "Comic Halftone",
                        subtitle = "Vintage pop-art newsprint #FFFDF0 with Ben-Day dot styling",
                        selected = uiState.theme == AppTheme.COMIC,
                        onSelect = { viewModel.setTheme(AppTheme.COMIC) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Reader Defaults
            SectionHeader(title = "Reader Defaults", icon = Icons.AutoMirrored.Filled.MenuBook)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Default Reading Mode",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    ThemeOptionRow(
                        title = "Horizontal Pager (Left to Right)",
                        subtitle = "Western comic page-by-page flow",
                        selected = uiState.readingMode == ReadingMode.HORIZONTAL && uiState.readingDirection == ReadingDirection.LTR,
                        onSelect = {
                            viewModel.setReadingMode(ReadingMode.HORIZONTAL)
                            viewModel.setReadingDirection(ReadingDirection.LTR)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ThemeOptionRow(
                        title = "Horizontal Pager (Right to Left)",
                        subtitle = "Japanese Manga page-by-page flow",
                        selected = uiState.readingMode == ReadingMode.HORIZONTAL && uiState.readingDirection == ReadingDirection.RTL,
                        onSelect = {
                            viewModel.setReadingMode(ReadingMode.HORIZONTAL)
                            viewModel.setReadingDirection(ReadingDirection.RTL)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ThemeOptionRow(
                        title = "Vertical Continuous Scroll",
                        subtitle = "Webtoon and Manhwa continuous vertical flow",
                        selected = uiState.readingMode == ReadingMode.VERTICAL,
                        onSelect = { viewModel.setReadingMode(ReadingMode.VERTICAL) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Navigation & Controls
            SectionHeader(title = "Navigation & Display Controls", icon = Icons.AutoMirrored.Filled.VolumeUp)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Volume Key Navigation Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Volume Key Navigation",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Use hardware Volume Up/Down buttons to turn pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = uiState.volumeKeyNav,
                            onCheckedChange = { viewModel.setVolumeKeyNav(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Keep Screen Awake Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keep Screen Awake",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Prevent the screen from sleeping while reading",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = uiState.keepScreenAwake,
                            onCheckedChange = { viewModel.setKeepScreenAwake(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: Watched Directory
            SectionHeader(title = "Library Storage", icon = Icons.Default.Folder)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            folderText = uiState.watchedDirectory
                            showFolderDialog = true
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Watched Comics Directory",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.watchedDirectory,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            folderPickerLauncher.launch(null)
                        }) {
                            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Pick Folder")
                        }
                        IconButton(onClick = {
                            folderText = uiState.watchedDirectory
                            showFolderDialog = true
                        }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Folder Path")
                        }
                    }
                }
            }
        }
    }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Set Watched Folder") },
            text = {
                OutlinedTextField(
                    value = folderText,
                    onValueChange = { folderText = it },
                    label = { Text("Absolute directory path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderText.isNotBlank()) {
                        viewModel.setWatchedDirectory(folderText.trim())
                    }
                    showFolderDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun resolveUriToPath(uri: Uri): String? {
    if (uri.scheme == "content" && DocumentsContract.isTreeUri(uri)) {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val split = documentId.split(":")
        if (split.size >= 2) {
            val type = split[0]
            val path = split[1]

            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().toString() + "/" + path
            } else {
                // Return a path that works with external SD cards using standard Android path structure
                return "/storage/$type/$path"
            }
        }
    }
    return null
}
