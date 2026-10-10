package app.keeply.ui.features.detail

import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.outlined.ContentCopy
import java.time.Instant
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.outlined.Share
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.keeply.AppContainer
import app.keeply.BuildConfig
import app.keeply.core.domain.RelativeDays
import app.keeply.core.domain.ReminderSchedule
import app.keeply.core.domain.nextOccurrence
import app.keeply.notifications.ReminderText
import app.keeply.ui.features.reminders.alertLabel
import app.keeply.core.domain.SubscriptionMath
import app.keeply.core.domain.WarrantyRules
import app.keeply.core.domain.WarrantyStatus
import app.keeply.core.model.Document
import app.keeply.core.model.ItemMeta
import app.keeply.core.model.ReminderNote
import app.keeply.core.model.ItemType
import app.keeply.core.model.Receipt
import app.keeply.core.model.SavedLink
import app.keeply.core.model.Subscription
import app.keeply.core.model.SubscriptionStatus
import app.keeply.core.model.Warranty
import app.keeply.data.settings.NotificationCategory
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.ConfirmDialog
import app.keeply.ui.components.GroupedCard
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.LogoTile
import app.keeply.ui.components.SiteIconTile
import app.keeply.ui.components.PhotoViewer
import app.keeply.ui.components.PhotosField
import app.keeply.ui.components.RowDivider
import app.keeply.ui.components.TagTone
import app.keeply.ui.components.display
import app.keeply.ui.components.icon
import app.keeply.ui.components.iconFor
import app.keeply.ui.components.tint
import app.keeply.ui.features.common.ReminderPreviewButton
import app.keeply.ui.features.common.reminderLabel
import app.keeply.ui.navigation.ItemDetailRoute
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Whatever kind of item this is, loaded for read-only display. */
sealed interface ItemDetail {
    val meta: ItemMeta
    data class OfReceipt(val r: Receipt) : ItemDetail { override val meta get() = r.meta }
    data class OfWarranty(val w: Warranty) : ItemDetail { override val meta get() = w.meta }
    data class OfSubscription(val s: Subscription) : ItemDetail { override val meta get() = s.meta }
    data class OfLink(val l: SavedLink) : ItemDetail { override val meta get() = l.meta }
    data class OfDocument(val d: Document) : ItemDetail { override val meta get() = d.meta }
    data class OfReminder(val n: ReminderNote) : ItemDetail { override val meta get() = n.meta }
}

class ItemDetailViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val id: String = handle.toRoute<ItemDetailRoute>().id

    var detail by mutableStateOf<ItemDetail?>(null)
        private set
    var photos by mutableStateOf<List<String>>(emptyList())
        private set
    /** True once loading found nothing: the item was deleted (here or on another device). */
    var gone by mutableStateOf(false)
        private set

    /** Called on every resume, so returning from Edit shows the saved values. */
    fun reload() = viewModelScope.launch {
        val vault = container.vault
        val loaded = when (vault.typeOf(id)) {
            ItemType.RECEIPT -> vault.receipt(id)?.let { ItemDetail.OfReceipt(it) }
            ItemType.WARRANTY -> vault.warranty(id)?.let { ItemDetail.OfWarranty(it) }
            ItemType.SUBSCRIPTION -> vault.subscription(id)?.let { ItemDetail.OfSubscription(it) }
            ItemType.LINK -> vault.link(id)?.let { ItemDetail.OfLink(it) }
            ItemType.DOCUMENT -> vault.document(id)?.let { ItemDetail.OfDocument(it) }
            ItemType.REMINDER -> vault.reminderNote(id)?.let { ItemDetail.OfReminder(it) }
            else -> null
        }
        detail = loaded
        photos = vault.attachments(id).filter { it.kind == "PHOTO" }.sortedBy { it.position }.map { it.id }
        payments = if (loaded is ItemDetail.OfSubscription) vault.payments(id) else emptyList()
        gone = loaded == null
    }

    var payments by mutableStateOf<List<app.keeply.core.model.Payment>>(emptyList())
        private set

    fun markPaid() = act { container.vault.markSubscriptionPaid(id) }
    fun markDone(occurrence: java.time.LocalDateTime) = act { container.vault.markReminderDone(id, occurrence) }
    fun markRenewed(newExpiry: LocalDate) = act { container.vault.markDocumentRenewed(id, newExpiry) }
    fun markFlag() = act { container.vault.markFlag(id) }
    fun undo() = act { container.vault.undoMark(id) }
    fun toggleFavorite() = detail?.meta?.isFavorite?.let { fav -> act { container.vault.setFavorite(id, !fav) } }

    private fun act(block: suspend () -> Unit) = viewModelScope.launch { block(); reload() }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        container.vault.delete(id)
        onDone()
    }
}

/**
 * Read-only preview of an item, opened from any list. Changing anything goes through
 * Edit (which asks before saving) or Delete (which asks before deleting).
 */
@Composable
fun ItemDetailScreen(onBack: () -> Unit, onEdit: (ItemType, String) -> Unit) {
    val vm = keeplyViewModel { c, h -> ItemDetailViewModel(c, h) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }
    LaunchedEffect(vm.gone) { if (vm.gone) onBack() }
    BackHandler(onBack = onBack)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val c = Keeply.colors
    val d = vm.detail

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Surface(shape = Radius.pill, color = c.surfaceRaised, onClick = onBack, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = c.text) }
            }
            Text(d?.meta?.type?.label ?: "", style = Keeply.type.bodyMdMedium, color = c.textMuted, modifier = Modifier.weight(1f))
            if (d != null) {
                val context = LocalContext.current
                val fav = d.meta.isFavorite
                TopAction(
                    if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (fav) "Remove from favourites" else "Add to favourites",
                    tint = if (fav) c.warning else c.text, onClick = vm::toggleFavorite,
                )
                TopAction(Icons.Outlined.ContentCopy, "Copy details") {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(d.meta.title, shareText(d)))
                    // Android 13+ shows its own "Copied" confirmation.
                    if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                }
                TopAction(Icons.Outlined.Share, "Share") {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, d.meta.title).putExtra(Intent.EXTRA_TEXT, shareText(d))
                    context.startActivity(Intent.createChooser(send, "Share ${d.meta.type.label.lowercase()}"))
                }
            }
        }
        if (d == null) return@Column

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Header(d)
            MarkCard(d, vm)
            when (d) {
                is ItemDetail.OfReceipt -> ReceiptFacts(d.r)
                is ItemDetail.OfWarranty -> WarrantyFacts(d.w)
                is ItemDetail.OfSubscription -> SubscriptionFacts(d.s)
                is ItemDetail.OfLink -> LinkFacts(d.l)
                is ItemDetail.OfDocument -> DocumentFacts(d.d)
                is ItemDetail.OfReminder -> ReminderFacts(d.n)
            }
            // Any item can carry a web page: show its preview and a way to open or share it.
            if (d !is ItemDetail.OfLink) d.meta.url?.let { PageLinkCard(it) }
            if (BuildConfig.PHOTOS_ENABLED && vm.photos.isNotEmpty()) {
                var viewing by rememberSaveable { mutableStateOf<String?>(null) }
                KeeplyCard {
                    PhotosField("Photos", vm.photos, canAdd = false, importing = false, error = null, onAdd = {}, onOpen = { viewing = it })
                }
                viewing?.let { PhotoViewer(it, onDismiss = { viewing = null }, onDelete = null) }
            }
            if (d.meta.notes.isNotBlank()) {
                KeeplyCard {
                    Text(if (d.meta.type == ItemType.REMINDER) "Note" else "Notes", style = Keeply.type.captionBold, color = c.textMuted)
                    Text(d.meta.notes, style = Keeply.type.bodyMd, color = c.text)
                }
            }
            Text(
                "Added ${d.meta.createdAt.date().display()}" +
                    if (d.meta.updatedAt.date() != d.meta.createdAt.date() || d.meta.updatedAt.toEpochMilli() - d.meta.createdAt.toEpochMilli() > 60_000) " · Last edited ${d.meta.updatedAt.date().display()}" else "",
                style = Keeply.type.caption, color = c.textPlaceholder, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            Modifier.fillMaxWidth().background(c.surfaceRaised).padding(horizontal = Space.lg, vertical = Space.md).navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            KeeplyButton("Delete", { confirmDelete = true }, Modifier.weight(1f), variant = ButtonVariant.Danger, leadingIcon = Icons.Outlined.Delete)
            KeeplyButton("Edit", { onEdit(d.meta.type, vm.id) }, Modifier.weight(1f), leadingIcon = Icons.Outlined.Edit)
        }
    }

    if (confirmDelete && d != null) {
        ConfirmDialog(
            title = "Delete \"${d.meta.title}\"?",
            body = "It will be removed from your vault and your account, along with its reminders. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Header(d: ItemDetail) {
    val c = Keeply.colors
    val type = d.meta.type
    KeeplyCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            val logo = d.meta.logoId
            val icon = iconFor(type, (d as? ItemDetail.OfDocument)?.d?.kind)
            when {
                logo != null -> LogoTile(logo, size = 56.dp)
                d is ItemDetail.OfLink -> SiteIconTile(d.l.domain, fallback = { IconTile(icon, type.tint, size = 56.dp, iconSize = 28.dp) }, size = 56.dp)
                d.meta.url != null -> SiteIconTile(
                    app.keeply.core.domain.UrlParser.parse(d.meta.url.orEmpty())?.domain.orEmpty(),
                    fallback = { IconTile(icon, type.tint, size = 56.dp, iconSize = 28.dp) }, size = 56.dp,
                )
                else -> IconTile(icon, type.tint, size = 56.dp, iconSize = 28.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(d.meta.title, style = Keeply.type.heading3, color = c.text, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    KeeplyTag(type.label, TagTone.Neutral)
                    StatusTag(d)
                    if (d.meta.isDemo) KeeplyTag("Demo", TagTone.Neutral)
                }
            }
        }
    }
}

/** The one status that matters most for this item, spelled out. */
@Composable
private fun StatusTag(d: ItemDetail) {
    val today = LocalDate.now()
    when (d) {
        is ItemDetail.OfReceipt -> if (d.meta.markedAt != null) KeeplyTag("Refunded", TagTone.Neutral)
        is ItemDetail.OfLink -> if (d.meta.markedAt != null) KeeplyTag("Read", TagTone.Success)
        is ItemDetail.OfSubscription -> when {
            d.s.status == SubscriptionStatus.CANCELLED -> KeeplyTag("Cancelled", TagTone.Gold)
            else -> dueTag(ChronoUnit.DAYS.between(today, d.s.nextBillingDate))
        }
        is ItemDetail.OfWarranty -> if (d.meta.markedAt != null) KeeplyTag("Claimed", TagTone.Success) else when (WarrantyRules.status(d.w.expiryDate, today)) {
            WarrantyStatus.EXPIRED -> KeeplyTag("Expired", TagTone.Danger)
            else -> {
                val days = ChronoUnit.DAYS.between(today, d.w.expiryDate)
                if (days <= 30) dueTag(days) else KeeplyTag("Active", TagTone.Success)
            }
        }
        is ItemDetail.OfReminder -> {
            val next = nextOf(d.n)
            when {
                next == null -> KeeplyTag(if (d.n.meta.doneThrough != null) "Done" else "Past", if (d.n.meta.doneThrough != null) TagTone.Success else TagTone.Neutral)
                else -> {
                    val days = ChronoUnit.DAYS.between(today, next.toLocalDate())
                    if (days <= 30) KeeplyTag(RelativeDays.countdown(days), TagTone.Danger) else KeeplyTag("Scheduled", TagTone.Success)
                }
            }
        }
        is ItemDetail.OfDocument -> {
            val expiry = d.d.expiryDate
            when {
                expiry == null -> KeeplyTag("No expiry", TagTone.Neutral)
                expiry.isBefore(today) -> KeeplyTag("Expired", TagTone.Danger)
                ChronoUnit.DAYS.between(today, expiry) <= 30 -> dueTag(ChronoUnit.DAYS.between(today, expiry))
                else -> KeeplyTag("Valid", TagTone.Success)
            }
        }
    }
}

@Composable
private fun dueTag(days: Long) {
    if (days in 0..30) KeeplyTag(RelativeDays.countdown(days), TagTone.Danger)
}

/** Round icon button in the preview's top bar, matching Back. */
@Composable
private fun TopAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = Keeply.colors.text, onClick: () -> Unit) {
    Surface(shape = Radius.pill, color = Keeply.colors.surfaceRaised, onClick = onClick, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun ReceiptFacts(r: Receipt) {
    BigNumber(r.total.format(), "Total paid")
    Facts(
        "Merchant" to r.merchant,
        "Purchase date" to r.purchaseDate.display(),
        "Category" to r.category,
        "Payment method" to r.paymentMethod,
        "Items" to r.lineItems.joinToString("\n"),
        "Reminder" to alertText(r.meta.remindAt, r.meta.remindRepeat),
    )
}

@Composable
private fun WarrantyFacts(w: Warranty) {
    val today = LocalDate.now()
    Facts(
        "Product" to w.productName,
        "Brand" to w.brand,
        "Model" to w.model,
        "Purchased" to w.purchaseDate.display(),
        "Cover ends" to "${w.expiryDate.display()} · ${RelativeDays.describeUntil(w.expiryDate, today, "expire")}",
        "Retailer" to w.retailer,
        "Serial number" to w.serialNumber,
        "Reminders" to reminders(w.reminderOffsetsDays),
    )
}

@Composable
private fun SubscriptionFacts(s: Subscription) {
    val today = LocalDate.now()
    val yearly = runCatching { SubscriptionMath.annualCost(s.price, s.cycle, s.customIntervalDays).format() }.getOrNull()
    BigNumber(s.price.format(), "${s.cycle.label}${yearly?.let { " · about $it a year" } ?: ""}")
    Facts(
        "Next payment" to if (s.status == SubscriptionStatus.ACTIVE) "${s.nextBillingDate.display()} · ${RelativeDays.describeUntil(s.nextBillingDate, today, "renew")}" else "Cancelled",
        "Category" to s.category,
        "Payment method" to s.paymentMethod,
        "Reminders" to if (s.status == SubscriptionStatus.ACTIVE) reminders(s.reminderOffsetsDays) else "",
    )
    if (s.status == SubscriptionStatus.ACTIVE) {
        KeeplyCard {
            ReminderPreviewButton(NotificationCategory.SUBSCRIPTIONS, s.serviceName, s.reminderOffsetsDays.minOrNull() ?: 1, s.meta.id)
        }
    }
}

@Composable
private fun DocumentFacts(d: Document) {
    val today = LocalDate.now()
    val c = Keeply.colors
    // The number stays masked until asked for, in case someone is looking over a shoulder.
    var showNumber by rememberSaveable { mutableStateOf(false) }
    Facts(
        "Type" to d.kind.label,
        "Holder" to d.holder,
        "Issued by" to d.issuer,
        "Issued" to (d.issueDate?.display() ?: ""),
        "Expires" to (d.expiryDate?.let { "${it.display()} · ${RelativeDays.describeUntil(it, today, "expire")}" } ?: "Doesn't expire"),
        "Reminders" to if (d.expiryDate != null) reminders(d.reminderOffsetsDays).ifBlank { "Off" } else "",
    )
    if (d.number.isNotBlank()) {
        KeeplyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Document number", style = Keeply.type.captionBold, color = c.textMuted)
                    Text(if (showNumber) d.number else mask(d.number), style = Keeply.type.heading5, color = c.text)
                }
                KeeplyButton(if (showNumber) "Hide" else "Show", { showNumber = !showNumber }, variant = ButtonVariant.Soft, size = app.keeply.ui.components.ButtonSize.Sm)
            }
        }
    }
    if (d.expiryDate != null && d.reminderOffsetsDays.isNotEmpty() && !d.expiryDate.isBefore(today)) {
        KeeplyCard {
            ReminderPreviewButton(NotificationCategory.DOCUMENTS, d.meta.title, d.reminderOffsetsDays.minOrNull() ?: 30, d.meta.id)
        }
    }
}

/** "Oct 5, 2026, 9:00 AM" for a future alert, "" when none is set or it has passed. */
private fun alertText(first: java.time.LocalDateTime?, repeat: app.keeply.core.model.Repeat): String {
    val next = first?.let { ReminderSchedule.nextFor(it, repeat, 0, java.time.LocalDateTime.now()) } ?: return ""
    val text = "${next.toLocalDate().display()}, ${next.toLocalTime().format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))}"
    return if (repeat == app.keeply.core.model.Repeat.NEVER) text else "$text · ${repeat.label.lowercase()}"
}

private fun nextOf(n: ReminderNote): java.time.LocalDateTime? =
    n.nextOccurrence(java.time.LocalDateTime.now())

@Composable
private fun ReminderFacts(n: ReminderNote) {
    val next = nextOf(n)
    val time = java.time.LocalTime.of(n.minuteOfDay / 60, n.minuteOfDay % 60).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
    val c = Keeply.colors
    KeeplyCard {
        Text(next?.let { "${it.toLocalDate().display()}, $time" } ?: "${n.date.display()}, $time", style = Keeply.type.heading3, color = c.text)
        Text(
            next?.let { RelativeDays.describeUntil(it.toLocalDate(), LocalDate.now(), "happen") } ?: "This has already happened",
            style = Keeply.type.bodySm, color = c.textMuted,
        )
    }
    Facts(
        "Type" to n.category.label,
        "Repeats" to n.repeat.label,
        "Alerts" to n.alertsMinutesBefore.sortedDescending().joinToString(", ", transform = ::alertLabel),
    )
    if (next != null) {
        KeeplyCard {
            ReminderPreviewButton(NotificationCategory.REMINDERS, n.meta.id, caption = "See exactly how the alert will look. Expand it to read your note.", details = n.meta.notes) {
                ReminderText.forNote(n.category.label, n.meta.title, n.alertsMinutesBefore.minOrNull() ?: 0, next)
            }
        }
    }
}

/** "•••• 4417": enough to recognise the document, not enough to copy it. */
private fun mask(number: String): String = "•••• " + number.takeLast(4)

@Composable
private fun PageLinkCard(url: String) {
    val context = LocalContext.current
    val fetcher = (context.applicationContext as app.keeply.KeeplyApp).container.linkMetadata
    val image by produceState<String?>(null, url) { value = fetcher.fetchImage(url) }
    val domain = app.keeply.core.domain.UrlParser.parse(url)?.domain ?: url
    KeeplyCard(spacing = Space.sm) {
        image?.let { img ->
            var failed by remember(img) { mutableStateOf(false) }
            if (!failed) coil.compose.AsyncImage(
                model = img, contentDescription = "Preview image from $domain",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, onError = { failed = true },
                modifier = Modifier.fillMaxWidth().aspectRatio(1.91f).clip(Radius.md),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            SiteIconTile(domain, fallback = { IconTile(Icons.AutoMirrored.Outlined.OpenInNew, Keeply.colors.tintBlue, size = 36.dp, iconSize = 18.dp) }, size = 36.dp)
            Text(domain, style = Keeply.type.bodyMdMedium, color = Keeply.colors.text, modifier = Modifier.weight(1f))
            KeeplyButton("Open", {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (ignored: ActivityNotFoundException) { }
            }, variant = ButtonVariant.Soft, size = app.keeply.ui.components.ButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
        }
    }
}

@Composable
private fun LinkFacts(l: SavedLink) {
    val context = LocalContext.current
    val fetcher = (context.applicationContext as app.keeply.KeeplyApp).container.linkMetadata
    // Looked up when the preview opens and shown from the website; nothing is stored.
    val image by produceState<String?>(null, l.url) { value = fetcher.fetchImage(l.url) }
    image?.let { url ->
        var failed by remember(url) { mutableStateOf(false) }
        if (!failed) {
            coil.compose.AsyncImage(
                model = url, contentDescription = "Preview image from ${l.domain}",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxWidth().aspectRatio(1.91f).clip(Radius.lg).background(Keeply.colors.fieldFill),
            )
        }
    }
    Facts(
        // Long tracking/sign-in URLs would fill the screen; Open and Share still use the full link.
        "Link" to if (l.url.length > 70) l.url.take(67) + "…" else l.url,
        "Website" to l.domain,
        "Collection" to l.collection,
        "Tags" to l.meta.tags.joinToString(", "),
        "Reminder" to alertText(l.meta.remindAt, l.meta.remindRepeat),
    )
    KeeplyButton(
        "Open link", {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url)))
            } catch (ignored: ActivityNotFoundException) {
            }
        },
        Modifier.fillMaxWidth(), variant = ButtonVariant.Soft, leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
    )
    KeeplyButton(
        "Share link", {
            val text = listOfNotNull(l.pageTitle.takeIf { it.isNotBlank() }, l.url).joinToString("\n")
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, "Share link"))
        },
        Modifier.fillMaxWidth(), variant = ButtonVariant.Outline, leadingIcon = Icons.Outlined.Share,
    )
}

@Composable
private fun BigNumber(value: String, caption: String) {
    KeeplyCard {
        Text(value, style = Keeply.type.stat, color = Keeply.colors.text)
        Text(caption, style = Keeply.type.bodySm, color = Keeply.colors.textMuted)
    }
}

/** Label/value rows; empty values are left out rather than shown as blanks. */
@Composable
private fun Facts(vararg rows: Pair<String, String>) {
    val shown = rows.filter { it.second.isNotBlank() }
    if (shown.isEmpty()) return
    val c = Keeply.colors
    GroupedCard {
        shown.forEachIndexed { i, (label, value) ->
            if (i > 0) RowDivider(startInset = Space.md)
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.Top) {
                Text(label, style = Keeply.type.bodySm, color = c.textMuted, modifier = Modifier.weight(0.4f))
                Text(value, style = Keeply.type.bodyMdMedium, color = c.text, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
            }
        }
    }
}

private fun reminders(offsets: List<Int>): String = offsets.sortedDescending().joinToString(", ", transform = ::reminderLabel)

private fun java.time.Instant.date(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private val SHORT_TIME: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
private fun java.time.Instant.shortDate(): String = atZone(ZoneId.systemDefault()).toLocalDate().display()

/**
 * The item's one "mark as" action and its undo: paid for subscriptions, done for reminders,
 * renewed for documents, claimed for warranties, refunded for receipts, read for links.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MarkCard(d: ItemDetail, vm: ItemDetailViewModel) {
    val c = Keeply.colors
    val meta = d.meta
    var pickRenewal by rememberSaveable { mutableStateOf(false) }

    // (status line, button label or null, action)
    val state: Triple<String?, String?, () -> Unit> = when (d) {
        is ItemDetail.OfSubscription -> {
            val s = d.s
            if (s.status != SubscriptionStatus.ACTIVE) Triple("Cancelled, so there's nothing to pay.", null) {}
            else Triple(
                "Next payment ${s.nextBillingDate.display()} · ${s.price.format()}",
                "Mark as paid",
            ) { vm.markPaid() }
        }
        is ItemDetail.OfReminder -> {
            val next = nextOf(d.n)
            when {
                next != null -> Triple("Next: ${next.toLocalDate().display()}, ${next.toLocalTime().format(SHORT_TIME)}", "Mark as done") { vm.markDone(next) }
                meta.doneThrough != null -> Triple("Completed", null) {}
                else -> Triple("This has already happened", null) {}
            }
        }
        is ItemDetail.OfDocument -> {
            val e = d.d.expiryDate
            if (e == null) Triple("This document doesn't expire.", null) {}
            else Triple(if (meta.canUndoMark) "Renewed · now expires ${e.display()}" else "Expires ${e.display()}", "Mark as renewed") { pickRenewal = true }
        }
        is ItemDetail.OfWarranty ->
            if (meta.markedAt != null) Triple("Claimed on ${meta.markedAt.shortDate()} · reminders off", null) {}
            else Triple("Used this warranty? Mark it claimed to stop its reminders.", "Mark as claimed") { vm.markFlag() }
        is ItemDetail.OfReceipt ->
            if (meta.markedAt != null) Triple("Refunded on ${meta.markedAt.shortDate()} · not counted in Spent", null) {}
            else Triple("Returned it? Mark it refunded and it won't count in Spent.", "Mark as refunded") { vm.markFlag() }
        is ItemDetail.OfLink ->
            if (meta.markedAt != null) Triple("Read on ${meta.markedAt.shortDate()}", null) {}
            else Triple(if (meta.remindAt != null) "Mark it read to stop its reminder." else "Done with this link?", "Mark as read") { vm.markFlag() }
    }
    val (status, label, action) = state

    KeeplyCard(spacing = Space.sm) {
        if (status != null) Text(status, style = Keeply.type.bodyMdMedium, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (label != null) {
                KeeplyButton(label, action, Modifier.weight(1f), size = app.keeply.ui.components.ButtonSize.Md, leadingIcon = androidx.compose.material.icons.Icons.Outlined.CheckCircle)
            }
            if (meta.canUndoMark) {
                KeeplyButton("Undo", { vm.undo() }, if (label == null) Modifier.fillMaxWidth() else Modifier, variant = ButtonVariant.Outline, size = app.keeply.ui.components.ButtonSize.Md)
            }
        }
        if (d is ItemDetail.OfSubscription && vm.payments.isNotEmpty()) {
            Text("Payments", style = Keeply.type.captionBold, color = c.textMuted, modifier = Modifier.padding(top = Space.xs))
            vm.payments.take(6).forEach { p ->
                Row {
                    Text("Paid ${p.paidOn.display()}", style = Keeply.type.bodySm, color = c.text, modifier = Modifier.weight(1f))
                    Text(p.amount.format(), style = Keeply.type.bodySmMedium, color = c.text)
                }
            }
        }
    }

    if (pickRenewal && d is ItemDetail.OfDocument) {
        val old = d.d.expiryDate ?: LocalDate.now()
        // Suggest the same validity again: from the old expiry, by the original length (or a year).
        val length = d.d.issueDate?.let { java.time.Period.between(it, old) }?.takeIf { !it.isNegative && !it.isZero } ?: java.time.Period.ofYears(1)
        val suggested = old.plus(length).let { if (it.isAfter(LocalDate.now())) it else LocalDate.now().plus(length) }
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = suggested.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickRenewal = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.markRenewed(Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }
                    pickRenewal = false
                }) { Text("Save new expiry", color = c.link) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pickRenewal = false }) { Text("Cancel", color = c.textMuted) } },
        ) {
            Text("When does the renewed ${d.d.kind.label.lowercase()} expire?", style = Keeply.type.heading5, color = c.text, modifier = Modifier.padding(start = Space.lg, top = Space.lg, end = Space.lg))
            androidx.compose.material3.DatePicker(state, title = null)
        }
    }
}
