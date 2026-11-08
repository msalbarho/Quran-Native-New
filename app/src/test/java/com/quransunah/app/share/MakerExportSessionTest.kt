package com.quransunah.app.share

import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MakerExportSessionTest {
    private fun file()=File.createTempFile("maker-session-",".mp4").apply { deleteOnExit() }
    @Test fun saveThenShareEncodesAndSavesOnlyOnce()=runBlocking {
        val session=MakerExportSession();var exports=0;var saves=0
        suspend fun obtain()=session.ensureSaved("same",{exports++;file()},{saves++})
        val saved=obtain();assertEquals(saved,obtain());assertEquals(1,exports);assertEquals(1,saves)
    }
    @Test fun changedInputsInvalidateFileAndGallerySave()=runBlocking {
        val session=MakerExportSession();var exports=0;var saves=0
        val a=session.ensureSaved("old",{exports++;file()},{saves++})
        val b=session.ensureSaved("new",{exports++;file()},{saves++})
        assertNotEquals(a,b);assertEquals(2,exports);assertEquals(2,saves)
    }
    @Test fun galleryFailureDoesNotForceAnotherExport()=runBlocking {
        val session=MakerExportSession();var exports=0;var saves=0
        try {session.ensureSaved("same",{exports++;file()},{saves++;error("gallery unavailable")});fail("must fail")}
        catch(_:IllegalStateException){}
        session.ensureSaved("same",{exports++;file()},{saves++})
        assertEquals(1,exports);assertEquals(2,saves)
    }
    @Test fun concurrentTapsShareOneExportAndSave()=runBlocking {
        val session=MakerExportSession();var exports=0;var saves=0
        val files=(1..2).map {async {session.ensureSaved("same",{exports++;delay(20);file()},{saves++})}}.awaitAll()
        assertEquals(files[0],files[1]);assertEquals(1,exports);assertEquals(1,saves)
    }
    @Test fun missingCachedFileIsRecreated()=runBlocking {
        val session=MakerExportSession();var exports=0;var saves=0
        val a=session.ensureSaved("same",{exports++;file()},{saves++});a.delete()
        session.ensureSaved("same",{exports++;file()},{saves++})
        assertEquals(2,exports);assertEquals(2,saves)
    }
}