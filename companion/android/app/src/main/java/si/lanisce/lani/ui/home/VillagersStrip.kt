package si.lanisce.lani.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.ui.stage.portraitPx
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.l10n.bi

/**
 * "Vaščani te čakajo · Villagers need you": a row of portraits with what each one asks for or what's
 * going on with them now. [onAll] (the register) shows when the cast is loaded.
 */
@Composable
fun VillagersStrip(needs: List<VillagerNeed>, onOpen: (VillagerNeed) -> Unit, onAll: (() -> Unit)?) {
    Column {
        SectionTitle(bi("villagersStrip.villagersNeed"), action = onAll?.let { bi("villagersStrip.all") to it })
        Row(
            Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Max).padding(PaddingValues(horizontal = 16.dp)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (n in needs) NeedCard(n) { onOpen(n) }
        }
    }
}

@Composable
private fun NeedCard(n: VillagerNeed, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(152.dp).fillMaxHeight(),
        shape = MaterialTheme.shapes.medium,
        colors = homeCardColors(),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp)) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    if (n.art != null) Portrait(n.art, modifier = Modifier.fillMaxSize(), px = portraitPx(76.dp), animate = false, seed = n.villagerId?.hashCode() ?: 0)
                    else Text(n.emoji, style = MaterialTheme.typography.headlineLarge)
                }
                if (n.art != null) Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).clearAndSetSemantics {},
                ) { Text(n.emoji, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(4.dp)) }
                if (n.more > 0) Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).semantics { contentDescription = bi("villagersStrip.moreMore", "more" to n.more) },
                ) {
                    Text("+${n.more}", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.clearAndSetSemantics {}.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(n.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (n.now) Text("💬 ${bi("villagersStrip.now")}", style = MaterialTheme.typography.labelSmall, color = AlpineGreen)
            Spacer(Modifier.height(2.dp))
            Text(
                n.request,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
