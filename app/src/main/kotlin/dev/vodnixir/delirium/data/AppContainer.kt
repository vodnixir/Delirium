package dev.vodnixir.delirium.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.data.local.PreferencesRepository
import dev.vodnixir.delirium.data.messaging.FcmTokenSyncer
import dev.vodnixir.delirium.data.photo.PhotoCache
import dev.vodnixir.delirium.data.photo.PhotoRepository

interface AppContainer {
    val appContext: Context
    val authRepository: AuthRepository
    val connectionRepository: ConnectionRepository
    val photoRepository: PhotoRepository
    val preferencesRepository: PreferencesRepository
    val photoCache: PhotoCache
    val fcmTokenSyncer: FcmTokenSyncer
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val appContext: Context = context.applicationContext

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    override val preferencesRepository: PreferencesRepository =
        PreferencesRepository(context.applicationContext)

    override val authRepository: AuthRepository =
        AuthRepository(firebaseAuth)

    override val connectionRepository: ConnectionRepository =
        ConnectionRepository(firestore, firebaseAuth)

    override val photoRepository: PhotoRepository =
        PhotoRepository(firestore, storage)

    override val photoCache: PhotoCache =
        PhotoCache(context.applicationContext)

    override val fcmTokenSyncer: FcmTokenSyncer =
        FcmTokenSyncer(firestore, firebaseAuth)
}
