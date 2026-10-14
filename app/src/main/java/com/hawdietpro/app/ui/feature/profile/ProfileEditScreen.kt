package com.hawdietpro.app.ui.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.SectionHeader

@Composable
fun ProfileEditScreen(onDone: (String?) -> Unit, viewModel: ProfileFormViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(form.saved) { if (form.saved) onDone("Profil & target diperbarui") }

    HawScaffold(
        title = "Data diri & target",
        onBack = { onDone(null) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::save,
                    enabled = form.valid && !form.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Simpan") }
            }
        },
    ) { padding ->
        if (!form.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@HawScaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Data diri")
            PersonalFields(form, viewModel::update)
            SectionHeader("Tubuh")
            BodyFields(form, viewModel::update)
            SectionHeader("Aktivitas")
            ActivityPicker(form, viewModel::update)
            SectionHeader("Tujuan")
            GoalPicker(form, viewModel::update)
            form.preview?.let {
                SectionHeader("Target harian")
                TargetSummary(it, form, viewModel::update)
            } ?: Text("Lengkapi data untuk melihat target.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
