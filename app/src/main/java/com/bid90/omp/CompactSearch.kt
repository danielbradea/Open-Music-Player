package com.bid90.omp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun SearchIcon() {
    Canvas(Modifier.size(22.dp)) {
        val ink = Color.White
        drawCircle(ink, size.width * .28f, Offset(size.width * .42f, size.height * .42f), style = Stroke(2.dp.toPx()))
        drawLine(ink, Offset(size.width * .63f, size.height * .63f), Offset(size.width * .88f, size.height * .88f),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
internal fun CompactSearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.search_library)
    LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }
    BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(44.dp)
            .background(Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp))
            .focusRequester(requester).semantics { contentDescription = label },
        decorationBox = { field ->
            Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchIcon()
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    field()
                }
                IconButton(onClick = onClose) {
                    Text("×", color = Color.White, modifier = Modifier.semantics {
                        contentDescription = label
                    })
                }
            }
        })
}
