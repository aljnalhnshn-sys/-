package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.theme.TawasulDarkGreen
import com.example.ui.theme.TawasulTeal
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    user: FirebaseUser,
    onProfileCompleted: (UserProfile) -> Unit,
    onSubmitProfile: (username: String, displayName: String, bio: String?, photoUrl: String?, onSuccess: (UserProfile) -> Unit, onError: (String) -> Unit) -> Unit
) {
    var displayName by remember { mutableStateOf(user.displayName ?: "") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("متاح في تواصل") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val usernameRegex = remember { Regex("^[a-z0-9_]{3,20}$") }
    val isUsernameValid = username.matches(usernameRegex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.profile_setup_title),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TawasulDarkGreen
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar Placeholder with first initial
            Surface(
                shape = CircleShape,
                color = TawasulTeal,
                modifier = Modifier.size(80.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (displayName.ifBlank { "ت" }).take(1),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.profile_setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Display Name field
            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                    errorMessage = null
                },
                label = { Text(stringResource(id = R.string.display_name_label)) },
                placeholder = { Text(stringResource(id = R.string.display_name_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TawasulTeal)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("display_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { input ->
                    // Normalize to lowercase without spaces
                    val cleaned = input.lowercase().replace(" ", "")
                    if (cleaned.length <= 20) {
                        username = cleaned
                        errorMessage = null
                    }
                },
                label = { Text(stringResource(id = R.string.username_label)) },
                placeholder = { Text(stringResource(id = R.string.username_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = TawasulTeal)
                },
                trailingIcon = {
                    if (isUsernameValid) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "اسم صالح",
                            tint = Color(0xFF2E7D32)
                        )
                    }
                },
                supportingText = {
                    Text(
                        text = stringResource(id = R.string.username_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (username.isNotBlank() && !isUsernameValid) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                isError = username.isNotBlank() && !isUsernameValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bio field
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 150) bio = it },
                label = { Text(stringResource(id = R.string.bio_label)) },
                placeholder = { Text(stringResource(id = R.string.bio_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null, tint = TawasulTeal)
                },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bio_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Button(
                onClick = {
                    if (displayName.trim().isEmpty()) {
                        errorMessage = "الرجاء إدخال الاسم الظاهر"
                        return@Button
                    }
                    if (!isUsernameValid) {
                        errorMessage = "اسم المستخدم غير صالح (3-20 حرف إنجليزي صغير أو أرقام)"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null

                    val photo = user.photoUrl?.toString()
                    onSubmitProfile(
                        username.trim(),
                        displayName.trim(),
                        bio.trim().ifEmpty { null },
                        photo,
                        { profile ->
                            isLoading = false
                            onProfileCompleted(profile)
                        },
                        { error ->
                            isLoading = false
                            errorMessage = error
                        }
                    )
                },
                enabled = !isLoading && isUsernameValid && displayName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("complete_profile_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = TawasulTeal),
                shape = RoundedCornerShape(26.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.btn_complete_profile),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
