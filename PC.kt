// MainActivity.kt package com.example.platinacoinapp

import android.os.Bundle import androidx.activity.ComponentActivity import androidx.activity.compose.setContent import androidx.compose.foundation.clickable import androidx.compose.foundation.layout.* import androidx.compose.foundation.rememberScrollState import androidx.compose.foundation.verticalScroll import androidx.compose.material.* import androidx.compose.runtime.* import androidx.compose.ui.Alignment import androidx.compose.ui.Modifier import androidx.compose.ui.unit.dp import androidx.compose.ui.unit.sp import androidx.lifecycle.* import androidx.room.* import kotlinx.coroutines.launch import kotlin.math.pow

// 1. ROOM SETUP @Entity(tableName = "users") data class UserEntity( @PrimaryKey val id: Int, val parentId: Int?, val level: Int, val storageMB: Int, val walletBalanceNOK: Int ) { val coins: Int get() = 4.0.pow(level).toInt() }

@Dao interface UserDao { @Query("SELECT * FROM users") suspend fun getAll(): List<UserEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insert(user: UserEntity)

@Update
suspend fun update(user: UserEntity)

}

@Database(entities = [UserEntity::class], version = 1) abstract class AppDatabase : RoomDatabase() { abstract fun userDao(): UserDao

companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: android.content.Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "platina_db"
            ).build().also { INSTANCE = it }
        }
    }
}

}

class UserRepository(private val dao: UserDao) { suspend fun getUsers() = dao.getAll() suspend fun addUser(user: UserEntity) = dao.insert(user) suspend fun updateUser(user: UserEntity) = dao.update(user) }

class UserViewModel(app: android.app.Application) : AndroidViewModel(app) { private val dao = AppDatabase.getDatabase(app).userDao() private val repository = UserRepository(dao)

var users = mutableStateListOf<UserEntity>()
    private set

init {
    viewModelScope.launch {
        users.clear()
        users.addAll(repository.getUsers())
        if (users.isEmpty()) {
            val root = UserEntity(1, null, 0, 0, 0)
            repository.addUser(root)
            users.add(root)
        }
    }
}

fun addUserUnder(parent: UserEntity) {
    if (users.count { it.parentId == parent.id } < 4) {
        val newUser = UserEntity(
            id = users.maxOf { it.id } + 1,
            parentId = parent.id,
            level = parent.level + 1,
            storageMB = 0,
            walletBalanceNOK = 0
        )
        viewModelScope.launch {
            repository.addUser(newUser)
            users.add(newUser)
        }
    }
}

fun updateStorage(user: UserEntity, addedMB: Int) {
    val updated = user.copy(storageMB = user.storageMB + addedMB)
    viewModelScope.launch {
        repository.updateUser(updated)
        val index = users.indexOfFirst { it.id == user.id }
        if (index != -1) users[index] = updated
    }
}

}

// 2. UI STARTER class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) setContent { val viewModel: UserViewModel = viewModel(factory = ViewModelProvider.AndroidViewModelFactory(application)) val currentUser = remember { mutableStateOf<UserEntity?>(null) }

if (currentUser.value == null) {
            UserTreePage(viewModel.users) { clicked ->
                currentUser.value = clicked
            }
        } else {
            UserDetailPage(currentUser.value!!, viewModel, onBack = {
                currentUser.value = null
            })
        }
    }
}

}

@Composable fun UserTreePage(users: List<UserEntity>, onUserClick: (UserEntity) -> Unit) { val root = users.find { it.parentId == null } ?: return Column( modifier = Modifier .fillMaxSize() .padding(16.dp) .verticalScroll(rememberScrollState()) ) { Text("Platina Coin App", fontSize = 24.sp) Spacer(modifier = Modifier.height(16.dp)) TreeRecursive(root, users, onUserClick) } }

@Composable fun TreeRecursive(user: UserEntity, all: List<UserEntity>, onUserClick: (UserEntity) -> Unit) { Card(modifier = Modifier .fillMaxWidth() .padding(4.dp) .clickable { onUserClick(user) }) { Column(modifier = Modifier.padding(12.dp)) { Text("Bruker ID: ${user.id} - Coins: ${user.coins}") Text("Lagring: ${user.storageMB} MB") } } val children = all.filter { it.parentId == user.id } Column(modifier = Modifier.padding(start = 16.dp)) { children.forEach { TreeRecursive(it, all, onUserClick) } } }

@Composable fun UserDetailPage(user: UserEntity, viewModel: UserViewModel, onBack: () -> Unit) { var inputMB by remember { mutableStateOf("") } Column( modifier = Modifier .fillMaxSize() .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally ) { Text("Bruker ${user.id} - Nivå ${user.level}", fontSize = 20.sp) Spacer(modifier = Modifier.height(8.dp)) Text("Platina Coins: ${user.coins}") Spacer(modifier = Modifier.height(8.dp)) OutlinedTextField( value = inputMB, onValueChange = { inputMB = it.filter { c -> c.isDigit() } }, label = { Text("Del lagring (MB)") } ) Spacer(modifier = Modifier.height(8.dp)) Button(onClick = { viewModel.updateStorage(user, inputMB.toIntOrNull() ?: 0) inputMB = "" }) { Text("Oppdater lagring") } Spacer(modifier = Modifier.height(8.dp)) Button(onClick = { viewModel.addUserUnder(user) }) { Text("Legg til bruker under") } Spacer(modifier = Modifier.height(8.dp)) Button(onClick = onBack) { Text("Tilbake") } } }

