package com.lifestyle.composemusicplayer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lifestyle.composemusicplayer.ui.theme.ComposeMusicPlayerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val isPlaying = MutableStateFlow(false)
    private val currentTrack = MutableStateFlow(Track())
    private val maxDuration = MutableStateFlow(0f)
    private val currentDuration = MutableStateFlow(0f)

    private var service: MusicPlayerService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, binder: IBinder?) {
            service = (binder as MusicPlayerService.MusicBinder).getService()
            binder.setMusicList(songs)
            lifecycleScope.launch {
                binder.isPlaying().collectLatest {
                    isPlaying.value = it
                }
            }
            lifecycleScope.launch {
                binder.currentDuration().collectLatest {
                    currentDuration.value = it
                }
            }
            lifecycleScope.launch {
                binder.maxDuration().collectLatest {
                    maxDuration.value = it
                }
            }
            lifecycleScope.launch {
                binder.getCurrentTract().collectLatest {
                    currentTrack.value = it
                }
            }
            isBound = true
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBound = false
        }

    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeMusicPlayerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(text = "Music Player")
                            },
                            actions = {
                                IconButton(onClick = {
                                    val intent =
                                        Intent(this@MainActivity, MusicPlayerService::class.java)
                                    startService(intent)
                                    bindService(intent, connection, BIND_AUTO_CREATE)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                }
                                IconButton(onClick = {
                                    if (isBound) {
                                        val intent =
                                            Intent(
                                                this@MainActivity,
                                                MusicPlayerService::class.java
                                            )
                                        stopService(intent)
                                        unbindService(connection)
                                        isBound = false
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null
                                    )
                                }
                            })
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                    {
                        val track by currentTrack.collectAsState()
                        val current by currentDuration.collectAsState()
                        val max by maxDuration.collectAsState()
                        val isPlaying by isPlaying.collectAsState()

                        Image(
                            painter = painterResource(track.image),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(
                                    RoundedCornerShape(24.dp)
                                ),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = track.name,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.2f",
                                    current.toDouble().div(1000)
                                )
                            )
                            Slider(
                                valueRange = 0f..max,
                                value = current,
                                onValueChange = {},
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = String.format(Locale.US, "%.2f", max.toDouble().div(1000)))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                service?.prev()
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_prev),
                                    contentDescription = null
                                )
                            }
                            IconButton(onClick = {
                                service?.playPause()
                            }) {
                                Icon(
                                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                    contentDescription = null
                                )
                            }
                            IconButton(onClick = {
                                service?.next()
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_next),
                                    contentDescription = null
                                )
                            }

                        }
                    }
                }
            }
        }
    }
}
