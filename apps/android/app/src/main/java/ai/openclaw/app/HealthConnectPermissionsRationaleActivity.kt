package ai.openclaw.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Required by Health Connect to show a rationale for why the app needs health permissions.
 * Also handles requesting Health Connect permissions via the standard permission flow.
 */
class HealthConnectPermissionsRationaleActivity : ComponentActivity() {

  private val PERMISSIONS = setOf(
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(BodyFatRecord::class),
    HealthPermission.getReadPermission(NutritionRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
  )

  private var permissionsGranted by mutableStateOf(false)

  private val requestPermissions = registerForActivityResult(
    PermissionController.createRequestPermissionResultContract()
  ) { granted ->
    permissionsGranted = granted.containsAll(PERMISSIONS)
    if (permissionsGranted) {
      Toast.makeText(this, "Health Connect permissions granted!", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Check if we already have permissions
    lifecycleScope.launch {
      val client = HealthConnectClient.getOrCreate(this@HealthConnectPermissionsRationaleActivity)
      val granted = client.permissionController.getGrantedPermissions()
      permissionsGranted = granted.containsAll(PERMISSIONS)
    }

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "OpenClaw Health Permissions",
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text = "OpenClaw reads your health data (weight, body composition, nutrition, and steps) " +
                "so your AI agent can help you track fitness goals, provide meal suggestions, " +
                "and monitor your progress over time. Your data stays on your device and is only " +
                "sent to your personal OpenClaw gateway.",
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(top = 16.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (!permissionsGranted) {
              Button(onClick = { requestPermissions.launch(PERMISSIONS) }) {
                Text("Grant Health Connect Permissions")
              }
            } else {
              Text(
                text = "✅ All health permissions granted!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}
