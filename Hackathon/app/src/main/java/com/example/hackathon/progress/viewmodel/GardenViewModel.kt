package com.example.hackathon.progress.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.hackathon.data.AppDatabase
import com.example.hackathon.progress.dao.DropDao
import com.example.hackathon.progress.dao.TreeDao

import com.example.hackathon.progress.entity.Drop
import com.example.hackathon.progress.entity.Tree
import kotlinx.coroutines.launch

class GardenViewModel(application: Application) : AndroidViewModel(application) {
    private val treeDao: TreeDao = AppDatabase.getDatabase(application).treeDao()
    private val dropDao: DropDao = AppDatabase.getDatabase(application).dropDao()

    private val _trees = MutableLiveData<List<Tree>>()
    val trees: LiveData<List<Tree>> = _trees

    private val _drops = MutableLiveData<List<Drop>>()
    val drops: LiveData<List<Drop>> = _drops

    fun insertTree(tree: Tree) {
        viewModelScope.launch {
            treeDao.insert(tree)
            loadTrees()
        }
    }

    fun insertDrop(drop: Drop) {
        viewModelScope.launch {
            dropDao.insert(drop)
            loadDrops()
        }
    }

    fun loadTrees() {
        viewModelScope.launch {
            _trees.postValue(treeDao.getAllTrees())
        }
    }

    fun loadDrops() {
        viewModelScope.launch {
            _drops.postValue(dropDao.getAllDrops())
        }
    }
}
