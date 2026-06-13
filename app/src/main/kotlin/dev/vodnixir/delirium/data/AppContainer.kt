package dev.vodnixir.delirium.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import dev.vodnixir.delirium.data.auth.AuthRepository
import dev.vodnixir.delirium.data.chat.ChatRepository
import dev.vodnixir.delirium.data.connection.ConnectionRepository
import dev.vodnixir.delirium.data.local.PreferencesRepository
import dev.vodnixir.delirium.data.messaging.FcmTokenSyncer
import dev.vodnixir.delirium.data.outbox.OutboxRepository
import dev.vodnixir.delirium.data.photo.PhotoCache
import dev.vodnixir.delirium.data.photo.PhotoRepository
import dev.vodnixir.delirium.data.reaction.ReactionsRepository

interface AppContainer {
    val appContext: Context
    val authRepository: AuthRepository
    val connectionRepository: ConnectionRepository
    val photoRepository: PhotoRepository
    val reactionsRepository: ReactionsRepository
    val chatRepository: ChatRepository
    val preferencesRepository: PreferencesRepository
    val photoCache: PhotoCache
    val fcmTokenSyncer: FcmTokenSyncer
    val outboxRepository: OutboxRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val appContext: Context = context.applicationContext

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    // Region must match the Cloud Functions deploy region (see functions/src/index.ts).
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west3")

    override val preferencesRepository: PreferencesRepository =
        PreferencesRepository(context.applicationContext)

    override val authRepository: AuthRepository =
        AuthRepository(firebaseAuth, functions)

    override val connectionRepository: ConnectionRepository =
        ConnectionRepository(firestore, firebaseAuth, context.applicationContext)

    override val photoRepository: PhotoRepository =
        PhotoRepository(firestore, storage, preferencesRepository)

    override val reactionsRepository: ReactionsRepository =
        ReactionsRepository(firestore, firebaseAuth)

    override val chatRepository: ChatRepository =
        ChatRepository(firestore, firebaseAuth)

    override val photoCache: PhotoCache =
        PhotoCache(context.applicationContext)

    override val fcmTokenSyncer: FcmTokenSyncer =
        FcmTokenSyncer(firestore, firebaseAuth)

    override val outboxRepository: OutboxRepository =
        OutboxRepository(context.applicationContext)
}
