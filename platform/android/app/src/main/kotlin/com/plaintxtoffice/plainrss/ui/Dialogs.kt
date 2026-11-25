package com.plaintxtoffice.plainrss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.plaintxtoffice.plainrss.R
import com.plaintxtoffice.plainrss.model.ArticleOrder
import com.plaintxtoffice.plainrss.model.DateDisplay
import com.plaintxtoffice.plainrss.model.FeedFolder
import com.plaintxtoffice.plainrss.model.FeedOrder
import com.plaintxtoffice.plainrss.model.ReaderPreferences
import kotlinx.coroutines.launch

/**
 * Dialog for subscribing to a new RSS or Atom feed URL.
 */
@Composable
fun AddFeedDialog(
  onDismiss: () -> Unit,
  onAddFeed: suspend (String) -> String?,
) {
  var urlText by rememberSaveable { mutableStateOf("") }
  var isAdding by remember { mutableStateOf(false) }
  var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val trimmed = urlText.trim()

  fun submit() {
    if (trimmed.isEmpty() || isAdding) return
    isAdding = true
    errorMessage = null
    scope.launch {
      val error = onAddFeed(trimmed)
      if (error != null) {
        errorMessage = error
        isAdding = false
      } else {
        onDismiss()
      }
    }
  }

  AlertDialog(
    onDismissRequest = { if (!isAdding) onDismiss() },
    title = { Text(stringResource(R.string.action_add_feed)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = urlText,
          onValueChange = { urlText = it },
          label = { Text(stringResource(R.string.add_feed_url_label)) },
          placeholder = { Text(stringResource(R.string.add_feed_url_placeholder)) },
          singleLine = true,
          enabled = !isAdding,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
          ),
          keyboardActions = KeyboardActions(onDone = { submit() }),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("addFeedUrlField"),
        )
        if (errorMessage != null) {
          Text(
            text = errorMessage ?: "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        } else {
          Text(
            text = stringResource(R.string.add_feed_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      if (isAdding) {
        CircularProgressIndicator(
          modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .size(24.dp),
          strokeWidth = 2.dp,
        )
      } else {
        Button(
          onClick = { submit() },
          enabled = trimmed.isNotEmpty(),
          modifier = Modifier.testTag("addFeedConfirmButton"),
        ) {
          Text(stringResource(R.string.action_add))
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isAdding,
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}

/**
 * Dialog for creating, renaming, and removing feed folders.
 */
@Composable
fun FolderManagerDialog(
  folders: List<FeedFolder>,
  onSaveFolder: (name: String, editingId: String?) -> String?,
  onRemoveFolder: (folderId: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var folderName by rememberSaveable { mutableStateOf("") }
  var editingFolderId by rememberSaveable { mutableStateOf<String?>(null) }
  var errorText by rememberSaveable { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.action_folders)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = if (editingFolderId == null) stringResource(R.string.folder_new_heading) else stringResource(R.string.folder_rename_heading),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
          value = folderName,
          onValueChange = { folderName = it },
          label = { Text(stringResource(R.string.folder_name_label)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("folderNameField"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              val err = onSaveFolder(folderName, editingFolderId)
              if (err != null) {
                errorText = err
              } else {
                folderName = ""
                editingFolderId = null
                errorText = null
              }
            },
            modifier = Modifier.testTag("saveFolderButton"),
          ) {
            Text(if (editingFolderId == null) stringResource(R.string.action_create_folder) else stringResource(R.string.action_save_folder_name))
          }
          if (editingFolderId != null) {
            OutlinedButton(
              onClick = {
                editingFolderId = null
                folderName = ""
                errorText = null
              }
            ) {
              Text(stringResource(R.string.action_cancel_rename))
            }
          }
        }
        if (errorText != null) {
          Text(
            text = errorText ?: "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }

        if (folders.isNotEmpty()) {
          HorizontalDivider()
          Text(
            text = stringResource(R.string.action_folders),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          for ((id, name) in folders) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
              )
              TextButton(
                onClick = {
                  editingFolderId = id
                  folderName = name
                  errorText = null
                }
              ) {
                Text(stringResource(R.string.action_rename))
              }
              TextButton(
                onClick = {
                  onRemoveFolder(id)
                  if (editingFolderId == id) {
                    editingFolderId = null
                    folderName = ""
                  }
                },
                colors = ButtonDefaults.textButtonColors(
                  contentColor = MaterialTheme.colorScheme.error
                ),
              ) {
                Text(stringResource(R.string.action_remove))
              }
            }
          }
        }

        Text(
          text = stringResource(R.string.folder_manager_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_done))
      }
    },
  )
}

/**
 * Dialog for configuring reader retention, auto-refresh, and sorting/date display preferences.
 */
@Composable
fun SettingsDialog(
  preferences: ReaderPreferences,
  hasFeeds: Boolean,
  onRetentionChange: (Int) -> Unit,
  onApplyRetentionToAll: () -> Unit,
  onRefreshOnOpenChange: (Boolean) -> Unit,
  onArticleOrderChange: (ArticleOrder) -> Unit,
  onFeedOrderChange: (FeedOrder) -> Unit,
  onDateDisplayChange: (DateDisplay) -> Unit,
  onDismiss: () -> Unit,
) {
  var confirmApplyRetention by remember { mutableStateOf(false) }

  if (confirmApplyRetention) {
    AlertDialog(
      onDismissRequest = { confirmApplyRetention = false },
      title = {
        Text(
          stringResource(
            R.string.apply_retention_dialog_title,
            preferences.articleRetention,
          )
        )
      },
      text = {
        Text(
          stringResource(R.string.apply_retention_dialog_message)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            confirmApplyRetention = false
            onApplyRetentionToAll()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        ) {
          Text(stringResource(R.string.apply_retention_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmApplyRetention = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.action_settings)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.settings_stored_articles_heading),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        DropdownSelector(
          label = stringResource(R.string.settings_articles_per_feed),
          selectedLabel = preferences.articleRetention.toString(),
          options = ReaderPreferences.RETENTION_OPTIONS,
          optionLabel = { it.toString() },
          onSelect = onRetentionChange,
        )
        OutlinedButton(
          onClick = { confirmApplyRetention = true },
          enabled = hasFeeds,
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(R.string.settings_apply_limit))
        }
        Text(
          text = pluralStringResource(
            R.plurals.settings_retention_note,
            preferences.articleRetention,
            preferences.articleRetention,
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Text(
          text = stringResource(R.string.settings_refreshing_heading),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.settings_refresh_on_open),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
          )
          Switch(
            checked = preferences.refreshOnOpen,
            onCheckedChange = onRefreshOnOpenChange,
          )
        }
        Text(
          text = stringResource(R.string.settings_refresh_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Text(
          text = stringResource(R.string.settings_display_heading),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        DropdownSelector(
          label = stringResource(R.string.settings_article_sorting),
          selectedLabel = preferences.articleOrder.displayLabel(),
          options = ArticleOrder.entries,
          optionLabel = { it.displayLabel() },
          onSelect = onArticleOrderChange,
        )
        DropdownSelector(
          label = stringResource(R.string.settings_feed_sorting),
          selectedLabel = preferences.feedOrder.displayLabel(),
          options = FeedOrder.entries,
          optionLabel = { it.displayLabel() },
          onSelect = onFeedOrderChange,
        )
        DropdownSelector(
          label = stringResource(R.string.settings_article_dates),
          selectedLabel = preferences.dateDisplay.displayLabel(),
          options = DateDisplay.entries,
          optionLabel = { it.displayLabel() },
          onSelect = onDateDisplayChange,
        )
        Text(
          text = stringResource(R.string.settings_dates_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Text(
          text = stringResource(R.string.settings_storage_heading),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = stringResource(R.string.settings_storage_library_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = stringResource(R.string.settings_storage_cache_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_done))
      }
    },
  )
}

@Composable
private fun <T> DropdownSelector(
  label: String,
  selectedLabel: String,
  options: List<T>,
  optionLabel: @Composable (T) -> String,
  onSelect: (T) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = Modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
      value = selectedLabel,
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        .fillMaxWidth(),
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      for (option in options) {
        DropdownMenuItem(
          text = { Text(optionLabel(option)) },
          onClick = {
            expanded = false
            onSelect(option)
          },
        )
      }
    }
  }
}

/**
 * Offline privacy policy dialog matching the repository's privacy policy.
 */
@Composable
fun PrivacyPolicyDialog(
  onOpenContactUrl: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.action_privacy)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .testTag("privacyPolicyContent"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = stringResource(R.string.privacy_updated),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PolicySection(
          heading = stringResource(R.string.privacy_on_device_heading),
          body = stringResource(R.string.privacy_on_device_body),
        )
        PolicySection(
          heading = stringResource(R.string.privacy_servers_heading),
          body = stringResource(R.string.privacy_servers_body),
        )
        PolicySection(
          heading = stringResource(R.string.privacy_links_heading),
          body = stringResource(R.string.privacy_links_body),
        )
        PolicySection(
          heading = stringResource(R.string.privacy_removing_heading),
          body = stringResource(R.string.privacy_removing_body),
        )
        PolicySection(
          heading = stringResource(R.string.privacy_developer_heading),
          body = stringResource(R.string.privacy_developer_body),
        )
        PolicySection(
          heading = stringResource(R.string.privacy_feedback_heading),
          body = stringResource(R.string.privacy_feedback_body),
        )
        PolicySection(
          heading = stringResource(R.string.privacy_questions_heading),
          body = stringResource(R.string.privacy_questions_body),
        )
        TextButton(
          onClick = { onOpenContactUrl(CONTACT_URL) },
          modifier = Modifier.testTag("privacyContactLink"),
        ) {
          Text(stringResource(R.string.privacy_contact_link))
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("privacyPolicyDone"),
      ) {
        Text(stringResource(R.string.action_done))
      }
    },
  )
}

private enum class FeedbackError { NO_EMAIL_APP, NO_BROWSER }

/**
 * Feedback dialog offering an email draft for a bug report or feature request, and the contact
 * form.
 *
 * [onSendEmail] and [onOpenContactForm] return false when nothing on the device can handle the
 * request; the dialog then shows an error line naming the other option.
 */
@Composable
fun FeedbackDialog(
  onSendEmail: (FeedbackKind) -> Boolean,
  onOpenContactForm: () -> Boolean,
  onDismiss: () -> Unit,
) {
  var error by rememberSaveable { mutableStateOf<FeedbackError?>(null) }

  fun sendEmail(kind: FeedbackKind) {
    error = if (onSendEmail(kind)) null else FeedbackError.NO_EMAIL_APP
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.action_send_feedback)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(R.string.feedback_intro),
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          text = stringResource(R.string.feedback_bug_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
          onClick = { sendEmail(FeedbackKind.BUG_REPORT) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("feedbackReportBugButton"),
        ) {
          Text(stringResource(R.string.action_report_bug))
        }
        OutlinedButton(
          onClick = { sendEmail(FeedbackKind.FEATURE_REQUEST) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("feedbackRequestFeatureButton"),
        ) {
          Text(stringResource(R.string.action_request_feature))
        }
        TextButton(
          onClick = { error = if (onOpenContactForm()) null else FeedbackError.NO_BROWSER },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("feedbackContactFormButton"),
        ) {
          Text(stringResource(R.string.action_open_contact_form))
        }
        val errorMessage = when (error) {
          FeedbackError.NO_EMAIL_APP -> stringResource(R.string.feedback_no_email_app)
          FeedbackError.NO_BROWSER -> stringResource(R.string.feedback_no_browser)
          null -> null
        }
        if (errorMessage != null) {
          Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("feedbackError"),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("feedbackDone"),
      ) {
        Text(stringResource(R.string.action_done))
      }
    },
  )
}

@Composable
private fun PolicySection(heading: String, body: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = heading,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Recovery screen displayed when the persistent library file cannot be opened.
 */
@Composable
fun StorageRecoveryDialog(
  errorDetail: String,
  onRetry: () -> Unit,
  onContinueWithoutSaving: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = {},
    title = { Text(stringResource(R.string.storage_recovery_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          stringResource(R.string.storage_recovery_message)
        )
        Text(
          text = errorDetail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.storage_recovery_instructions),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    confirmButton = {
      Button(onClick = onRetry) {
        Text(stringResource(R.string.action_retry))
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onContinueWithoutSaving) {
        Text(stringResource(R.string.action_continue_without_saving))
      }
    },
  )
}
