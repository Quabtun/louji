package com.quabtun.louji.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quabtun.louji.R

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    val confirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlocked()
        }
    }

    @Suppress("DEPRECATION")
    fun requestConfirm() {
        // If the device no longer has a secure lock, there is nothing to verify against;
        // fall back to showing the app rather than locking the rider out permanently.
        if (keyguard == null || !keyguard.isKeyguardSecure) {
            onUnlocked()
            return
        }
        val intent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            keyguard.createConfirmDeviceCredentialIntent("楼记已锁定", "请验证系统锁屏以继续")
        } else {
            @Suppress("DEPRECATION")
            keyguard.createConfirmDeviceCredentialIntent("楼记已锁定", null)
        }
        if (intent != null) {
            runCatching { confirmLauncher.launch(intent) }.onFailure { onUnlocked() }
        } else {
            onUnlocked()
        }
    }

    LaunchedEffect(Unit) { requestConfirm() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("app_lock"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFF121517)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_louji_mark),
                    contentDescription = null,
                    modifier = Modifier.size(width = 46.dp, height = 29.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "楼记已锁定",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "使用系统锁屏密码或生物识别继续。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { requestConfirm() },
                modifier = Modifier.testTag("app_lock_unlock"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text("解锁", fontWeight = FontWeight.Bold)
            }
        }
    }
}
