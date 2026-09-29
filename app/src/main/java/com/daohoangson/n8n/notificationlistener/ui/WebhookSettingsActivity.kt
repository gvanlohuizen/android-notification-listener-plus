package com.daohoangson.n8n.notificationlistener.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daohoangson.n8n.notificationlistener.config.FilterRule
import com.daohoangson.n8n.notificationlistener.config.WebhookConfig
import com.daohoangson.n8n.notificationlistener.config.WebhookConfigStore
import com.daohoangson.n8n.notificationlistener.config.WebhookUrl
import com.daohoangson.n8n.notificationlistener.data.repository.NotificationRepository
import com.daohoangson.n8n.notificationlistener.ui.theme.MyApplicationTheme
import com.daohoangson.n8n.notificationlistener.utils.NotificationData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

@AndroidEntryPoint
class WebhookSettingsActivity : ComponentActivity() {

    @Inject
    lateinit var configStore: WebhookConfigStore

    @Inject
    lateinit var repository: NotificationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WebhookSettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        configStore = configStore,
                        repository = repository
                    )
                }
            }
        }
    }
}

// Index -1 means "adding a new webhook"
private const val NEW_WEBHOOK = -1

@Composable
fun WebhookSettingsScreen(
    modifier: Modifier = Modifier,
    configStore: WebhookConfigStore,
    repository: NotificationRepository
) {
    val config by configStore.config.collectAsState()
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deletingIndex by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    editingIndex?.let { index ->
        WebhookEditDialog(
            initial = config.urls.getOrNull(index),
            onDismiss = { editingIndex = null },
            onSave = { webhook ->
                val urls = config.urls.toMutableList()
                if (index == NEW_WEBHOOK) urls.add(webhook) else urls[index] = webhook
                configStore.save(config.copy(urls = urls))
                editingIndex = null
            }
        )
    }

    deletingIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { deletingIndex = null },
            title = { Text("Delete webhook?") },
            text = { Text(config.urls.getOrNull(index)?.name ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    configStore.save(config.copy(urls = config.urls.filterIndexed { i, _ -> i != index }))
                    deletingIndex = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingIndex = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Webhooks",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Notifications matching a webhook's rules are sent to its URL. " +
                    "Use * as the package name to match every app.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { editingIndex = NEW_WEBHOOK }) {
                Text("Add webhook")
            }
        }

        if (config.urls.isEmpty()) {
            item {
                Text(
                    text = "No webhooks yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }

        itemsIndexed(config.urls) { index, webhook ->
            WebhookCard(
                webhook = webhook,
                onEdit = { editingIndex = index },
                onDelete = { deletingIndex = index },
                onTest = {
                    coroutineScope.launch {
                        val payload = NotificationData(
                            packageName = context.packageName,
                            title = "Test notification",
                            text = "Sent from the webhook settings screen",
                            timestamp = System.currentTimeMillis(),
                            id = 0,
                            tag = "test"
                        ).toJson()
                        val success = repository.sendToWebhook(payload, webhook)
                        Toast.makeText(
                            context,
                            if (success) "Test sent to ${webhook.name}" else "Test to ${webhook.name} failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            IgnoredPackagesEditor(
                config = config,
                onSave = { packages ->
                    configStore.save(config.copy(ignoredPackages = packages))
                    Toast.makeText(context, "Ignored apps saved", Toast.LENGTH_SHORT).show()
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun WebhookCard(
    webhook: WebhookUrl,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = webhook.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = webhook.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (webhook.rules.isEmpty()) {
                Text(
                    text = "No rules: only receives notifications you upload manually",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            webhook.rules.forEach { rule ->
                Text(text = "• ${describeRule(rule)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text("Edit") }
                OutlinedButton(onClick = onTest) { Text("Test") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private fun describeRule(rule: FilterRule): String {
    val app = if (rule.packageName == FilterRule.ANY_PACKAGE) "Any app" else rule.packageName
    val parts = mutableListOf(app)
    rule.titleRegex?.let { parts.add("title ~ ${it.pattern}") }
    rule.textRegex?.let { parts.add("text ~ ${it.pattern}") }
    return parts.joinToString(", ")
}

private class RuleDraft(packageName: String, titleRegex: String, textRegex: String) {
    var packageName by mutableStateOf(packageName)
    var titleRegex by mutableStateOf(titleRegex)
    var textRegex by mutableStateOf(textRegex)
}

@Composable
fun WebhookEditDialog(
    initial: WebhookUrl?,
    onDismiss: () -> Unit,
    onSave: (WebhookUrl) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    val rules = remember {
        mutableStateListOf<RuleDraft>().apply {
            initial?.rules?.forEach {
                add(RuleDraft(it.packageName, it.titleRegex?.pattern ?: "", it.textRegex?.pattern ?: ""))
            }
            if (isEmpty()) add(RuleDraft(FilterRule.ANY_PACKAGE, "", ""))
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add webhook" else "Edit webhook") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Webhook URL") },
                    placeholder = { Text("https://your-n8n/webhook/...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Rules (any matching rule sends the notification)",
                    style = MaterialTheme.typography.titleSmall
                )
                rules.forEachIndexed { index, rule ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = rule.packageName,
                                onValueChange = { rule.packageName = it },
                                label = { Text("Package name (* = any app)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rule.titleRegex,
                                onValueChange = { rule.titleRegex = it },
                                label = { Text("Title regex (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rule.textRegex,
                                onValueChange = { rule.textRegex = it },
                                label = { Text("Text regex (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(onClick = { rules.removeAt(index) }) {
                                Text("Remove rule")
                            }
                        }
                    }
                }
                TextButton(onClick = { rules.add(RuleDraft("", "", "")) }) {
                    Text("Add rule")
                }

                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = buildWebhook(name, url, rules)
                result.onSuccess(onSave).onFailure { error = it.message }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun buildWebhook(name: String, url: String, drafts: List<RuleDraft>): Result<WebhookUrl> {
    val trimmedUrl = url.trim()
    if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
        return Result.failure(IllegalArgumentException("URL must start with http:// or https://"))
    }
    if (trimmedUrl.toHttpUrlOrNull() == null) {
        return Result.failure(IllegalArgumentException("URL is not valid"))
    }

    val rules = mutableListOf<FilterRule>()
    for (draft in drafts) {
        val packageName = draft.packageName.trim()
        if (packageName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Every rule needs a package name (or *)"))
        }
        val titleRegex = draft.titleRegex.trim().ifEmpty { null }?.let {
            it.toRegexOrNull() ?: return Result.failure(IllegalArgumentException("Invalid title regex: $it"))
        }
        val textRegex = draft.textRegex.trim().ifEmpty { null }?.let {
            it.toRegexOrNull() ?: return Result.failure(IllegalArgumentException("Invalid text regex: $it"))
        }
        rules.add(FilterRule(packageName, titleRegex, textRegex))
    }

    return Result.success(
        WebhookUrl(
            url = trimmedUrl,
            name = name.trim().ifEmpty { trimmedUrl },
            rules = rules
        )
    )
}

private fun String.toRegexOrNull(): Regex? = try {
    toRegex()
} catch (e: IllegalArgumentException) {
    null
}

@Composable
fun IgnoredPackagesEditor(
    config: WebhookConfig,
    onSave: (List<String>) -> Unit
) {
    var text by remember(config.ignoredPackages) {
        mutableStateOf(config.ignoredPackages.joinToString("\n"))
    }

    Text(text = "Ignored apps", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Package names, one per line. Notifications from these apps are dropped.",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = {
        onSave(text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct())
    }) {
        Text("Save ignored apps")
    }
}
