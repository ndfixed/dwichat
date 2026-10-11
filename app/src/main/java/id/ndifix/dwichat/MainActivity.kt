package id.ndifix.dwichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Green = Color(0xFF168653)
private val PaleGreen = Color(0xFFE6F6ED)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green, background = Color(0xFFF7FAF8))) { DwiChatApp() } }
    }
}

@Composable
private fun DwiChatApp() {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var myId by remember { mutableStateOf("") }
    var loggedInName by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(listOf<UserDto>()) }
    var conversations by remember { mutableStateOf(listOf<ConversationDto>()) }
    var selected by remember { mutableStateOf<ConversationDto?>(null) }
    var messages by remember { mutableStateOf(listOf<MessageDto>()) }
    var draft by remember { mutableStateOf("") }
    var socket by remember { mutableStateOf<ChatSocket?>(null) }

    fun authHeader() = "Bearer $token"
    fun refreshConversations() { scope.launch { runCatching { ApiClient.api.conversations(authHeader()).conversations }.onSuccess { conversations = it }.onFailure { error = it.message ?: "Gagal memuat percakapan" } } }
    fun openConversation(c: ConversationDto) {
        selected = c
        scope.launch { runCatching { ApiClient.api.messages(authHeader(), c.id).messages }.onSuccess { messages = it }.onFailure { error = it.message ?: "Gagal memuat pesan" } }
        socket?.join(c.id)
    }

    LaunchedEffect(token) {
        if (token.isNotBlank()) {
            socket?.close()
            socket = ChatSocket(BuildConfig.API_BASE_URL.removeSuffix("/"), token).also { s ->
                s.onMessage { msg -> messages = (messages.filterNot { it.id == msg.id } + msg).sortedBy { it.createdAt } }
                s.connect()
            }
            refreshConversations()
        }
    }
    DisposableEffect(Unit) { onDispose { socket?.close() } }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF7FAF8)) {
        if (token.isBlank()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(68.dp).background(Green, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Text("d", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(20.dp))
                Text("dwiChat", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color(0xFF163B2A))
                Text("Ngobrol dekat, dari mana saja.", color = Color.Gray)
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    busy = true; error = ""
                    scope.launch {
                        runCatching { if (isRegister) ApiClient.api.register(AuthRequest(username.trim(), password)) else ApiClient.api.login(AuthRequest(username.trim(), password)) }
                            .onSuccess { token = it.token; myId = it.user.id; loggedInName = it.user.username }
                            .onFailure { error = it.message ?: "Tidak dapat terhubung ke server" }
                        busy = false
                    }
                }, enabled = !busy && username.isNotBlank() && password.length >= 8, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (isRegister) "Buat akun" else "Masuk")
                }
                TextButton(onClick = { isRegister = !isRegister; error = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (isRegister) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar") }
                Text("Password minimal 8 karakter. Gunakan server yang sudah dikonfigurasi.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        } else if (selected != null) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = null; messages = emptyList(); refreshConversations() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali") }
                    Column(Modifier.weight(1f)) { Text(selected!!.otherUser.username, fontWeight = FontWeight.SemiBold); Text("Chat pribadi", style = MaterialTheme.typography.bodySmall, color = Green) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages, key = { it.id }) { m ->
                        val mine = m.senderId == myId
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Surface(color = if (mine) PaleGreen else Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp, modifier = Modifier.widthIn(max = 300.dp)) { Text(m.body, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Tulis pesan…") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { val text = draft.trim(); val c = selected; if (text.isNotEmpty() && c != null) { socket?.send(c.id, text); draft = "" } }, enabled = draft.isNotBlank()) { Icon(Icons.Default.Send, contentDescription = "Kirim", tint = Green) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Color.White).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("dwiChat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163B2A)); Text("Halo, @$loggedInName", color = Color.Gray) }
                    TextButton(onClick = { socket?.close(); socket = null; token = ""; selected = null; messages = emptyList() }) { Text("Keluar") }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Cari username…") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, shape = RoundedCornerShape(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { scope.launch { runCatching { ApiClient.api.searchUsers(authHeader(), query.trim()).users }.onSuccess { users = it }.onFailure { error = it.message ?: "Pencarian gagal" } } }, enabled = query.trim().isNotEmpty()) { Text("Cari") }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                if (users.isNotEmpty()) {
                    Text("Pengguna", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.heightIn(max = 220.dp)) { items(users, key = { it.id }) { user ->
                        Row(Modifier.fillMaxWidth().clickable {
                            scope.launch { runCatching { ApiClient.api.createConversation(authHeader(), CreateConversationRequest(user.id)) }.onSuccess { c -> users = emptyList(); query = ""; refreshConversations(); openConversation(c) }.onFailure { error = it.message ?: "Gagal membuka chat" } }
                        }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(PaleGreen, CircleShape), contentAlignment = Alignment.Center) { Text(user.username.take(1).uppercase(), color = Green, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(12.dp)); Text("@${user.username}", fontWeight = FontWeight.Medium)
                        }
                    } }
                }
                Text("Percakapan", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
                if (conversations.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Belum ada chat. Cari username untuk memulai.", color = Color.Gray) }
                else LazyColumn { items(conversations, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { openConversation(c) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(PaleGreen, CircleShape), contentAlignment = Alignment.Center) { Text(c.otherUser.username.take(1).uppercase(), color = Green, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(c.otherUser.username, fontWeight = FontWeight.SemiBold); Text(c.lastMessage ?: "Mulai percakapan", color = Color.Gray, maxLines = 1) }
                    }
                    HorizontalDivider(color = Color(0xFFE9EFEB), modifier = Modifier.padding(start = 76.dp))
                } }
            }
        }
    }
}
