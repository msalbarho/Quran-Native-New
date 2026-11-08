package com.quransunah.app.share

import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One editor session: Save and Share use the same encoded file and MediaStore entry.
 * A failed gallery save can be retried without encoding again; changed inputs invalidate it.
 */
class MakerExportSession(private val onEvent:(String)->Unit={}) {
    private val mutex=Mutex()
    private var cachedKey:Any?=null
    private var cachedFile:File?=null
    private var saved=false
    suspend fun ensureSaved(key:Any, create:suspend()->File, save:suspend(File)->Unit):File = mutex.withLock {
        if(cachedKey!=key || cachedFile?.exists()!=true) {
            onEvent("export key=${key.hashCode()}")
            val file=create()
            cachedKey=key;cachedFile=file;saved=false
        }
        else onEvent("reuse key=${key.hashCode()}")
        val file=checkNotNull(cachedFile)
        if(!saved){onEvent("gallery-save key=${key.hashCode()}");save(file);saved=true}
        file
    }
}

data class MakerExportKey(val content:AyahShareContent,val reciterId:String,val options:VideoMakerOptions)