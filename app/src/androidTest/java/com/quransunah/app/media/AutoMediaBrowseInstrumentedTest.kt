package com.quransunah.app.media

import android.content.ComponentName
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simulates Android Auto media browse against [PlaybackService]
 * without requiring a physical head unit.
 */
@RunWith(AndroidJUnit4::class)
class AutoMediaBrowseInstrumentedTest {
    @Test
    fun browseRootAndReciters() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val connected = CountDownLatch(1)
        val rootChildren = CountDownLatch(1)
        val errors = mutableListOf<String>()
        val childrenCount = AtomicInteger(0)
        val browserRef = AtomicReference<MediaBrowserCompat>()

        instrumentation.runOnMainSync {
            val browser = MediaBrowserCompat(
                context,
                ComponentName(context, PlaybackService::class.java),
                object : MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() {
                        val b = browserRef.get() ?: return
                        connected.countDown()
                        b.subscribe(
                            b.root,
                            object : MediaBrowserCompat.SubscriptionCallback() {
                                override fun onChildrenLoaded(
                                    parentId: String,
                                    children: MutableList<MediaItem>,
                                ) {
                                    childrenCount.set(children.size)
                                    rootChildren.countDown()
                                }

                                override fun onError(parentId: String) {
                                    errors += "subscribe error parent=$parentId"
                                    rootChildren.countDown()
                                }
                            },
                        )
                    }

                    override fun onConnectionFailed() {
                        errors += "connection failed"
                        connected.countDown()
                        rootChildren.countDown()
                    }

                    override fun onConnectionSuspended() {
                        errors += "connection suspended"
                    }
                },
                null,
            )
            browserRef.set(browser)
            browser.connect()
        }

        assertTrue("MediaBrowser connect timed out: $errors", connected.await(20, TimeUnit.SECONDS))
        val browser = browserRef.get()
        assertTrue("browser missing", browser != null)
        assertTrue("MediaBrowser not connected: $errors", browser!!.isConnected)
        assertTrue("Root children timed out: $errors", rootChildren.await(20, TimeUnit.SECONDS))
        assertTrue("errors=$errors", errors.isEmpty())
        assertTrue("expected root tabs, got=${childrenCount.get()}", childrenCount.get() >= 1)
        instrumentation.runOnMainSync { browser.disconnect() }
    }
}
