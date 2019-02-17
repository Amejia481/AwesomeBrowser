/*
 *  Copyright (c) 2019 Razeware LLC
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 *  distribute, sublicense, create a derivative work, and/or sell copies of the
 *  Software in any work that is designed, intended, or marketed for pedagogical or
 *  instructional purposes related to programming, coding, application development,
 *  or information technology.  Permission for such use, copying, modification,
 *  merger, publication, distribution, sublicensing, creation of derivative works,
 *  or sale is expressly withheld.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package com.raywenderlich.android.awesomebrowser

import android.content.Context
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.text.HtmlCompat.FROM_HTML_MODE_COMPACT
import android.support.v7.app.ActionBar
import android.support.v7.app.AlertDialog
import android.text.Html
import android.text.Spanned
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import org.mozilla.geckoview.*
import org.mozilla.geckoview.ContentBlocking.*
import org.mozilla.geckoview.GeckoSession.ProgressDelegate.SecurityInformation

class MainActivity : AppCompatActivity() {
  private lateinit var progressView: ProgressBar
  private lateinit var urlEditText: EditText
  private lateinit var geckoView: GeckoView
  private lateinit var trackersCount: TextView
  private val geckoSession = GeckoSession()
  private var trackersBlockedList: List<BlockEvent> = mutableListOf()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    progressView = findViewById(R.id.page_progress)

    setupToolbar()

    setupUrlEditText()

    setupGeckoView()

    setupTrackersCounter()

  }

  private fun setupUrlEditText() {
    urlEditText = findViewById(R.id.location_view)
    urlEditText.setOnEditorActionListener(object : View.OnFocusChangeListener, TextView.OnEditorActionListener {
      private var initialText: String? = null
      private var committed: Boolean = false

      override fun onFocusChange(view: View?, hasFocus: Boolean) {
        val textView = (view as TextView)
        if (hasFocus) {
          initialText = textView.text.toString()
          committed = false
        } else if (!committed) {
          textView.text = initialText
        }
      }

      override fun onEditorAction(textView: TextView, actionId: Int, event: KeyEvent?): Boolean {
        onCommit(textView.text.toString())
        committed = true
        textView.hideKeyboard()
        return true
      }

    })
  }

  fun onCommit(text: String) {
    clearTrackersCount()

    if ((text.contains(".") || text.contains(":")) && !text.contains(" ")) {
      geckoSession.loadUri(text)
    } else {
      geckoSession.loadUri(SEARCH_URI_BASE + text)
    }
    geckoView.requestFocus()
  }

  private fun clearTrackersCount() {
    trackersBlockedList = emptyList()
    trackersCount.text = "0"
  }

  private fun setupGeckoView() {
    geckoView = findViewById(R.id.geckoview)
    val runtime = GeckoRuntime.create(this)

    geckoSession.open(runtime)
    geckoView.setSession(geckoSession)
    geckoSession.loadUri(INITIAL_URL)
    urlEditText.setText(INITIAL_URL)

    geckoSession.progressDelegate = createProgressDelegate()
    geckoSession.contentBlockingDelegate = createBlockingDelegate()
    enableTrackingProtection()

  }


  private fun createBlockingDelegate(): ContentBlocking.Delegate {
    return ContentBlocking.Delegate { session, event ->
      trackersBlockedList += event
      trackersCount.text = "${trackersBlockedList.size}"
    }
  }

  private fun createProgressDelegate(): GeckoSession.ProgressDelegate {
    return object : GeckoSession.ProgressDelegate {
      override fun onPageStop(session: GeckoSession, success: Boolean) = Unit
      override fun onSecurityChange(session: GeckoSession, securityInfo: SecurityInformation) = Unit
      override fun onPageStart(session: GeckoSession, url: String) = Unit

      override fun onProgressChange(session: GeckoSession, progress: Int) {
        progressView.progress = progress

        if (progress in 1..99) {
          progressView.visibility = View.VISIBLE
        } else {
          progressView.visibility = View.GONE
        }
      }

    }
  }

  private fun setupTrackersCounter() {
    trackersCount = findViewById(R.id.trackers_count)
    trackersCount.text = "0"

    trackersCount.setOnClickListener {

      if (trackersBlockedList.isNotEmpty()) {
        val friendlyURLs = getFriendlyTrackersUrls()
        showDialog(friendlyURLs)
      }
    }
  }

  private fun getFriendlyTrackersUrls(): List<Spanned> {
    return trackersBlockedList.map { blockEvent ->

      val host = Uri.parse(blockEvent.uri).host
      val category = blockEvent.categoryToString()

      Html.fromHtml("<b><font color='#D55C7C'>[$category]</font></b> <br/> $host", FROM_HTML_MODE_COMPACT)

    }
  }

  private fun setupToolbar() {
    setSupportActionBar(findViewById(R.id.toolbar))
    supportActionBar?.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
  }

  private fun enableTrackingProtection(enable: Boolean = true) {
    geckoSession.settings.useTrackingProtection = enable
  }

  /**
   * Hides the soft input window.
   */
  private fun View.hideKeyboard() {
    val imm = (context.getSystemService(Context.INPUT_METHOD_SERVICE) ?: return)
        as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
  }

  private fun showDialog(items: List<Spanned>) {
    AlertDialog.Builder(this)
        .setTitle(getString(R.string.trackers_blocked))
        .setItems(items.toTypedArray(), null)
        .create()
        .show()

  }

  private fun BlockEvent.categoryToString(): String {
    val stringResource = when (categories) {
      NONE -> R.string.none
      AT_ANALYTIC -> R.string.analytic
      AT_AD -> R.string.ad
      AT_ALL -> R.string.all
      AT_TEST -> R.string.test
      SB_MALWARE -> R.string.malware
      SB_UNWANTED -> R.string.unwanted
      AT_SOCIAL -> R.string.social
      AT_CONTENT -> R.string.content
      SB_HARMFUL -> R.string.harmful
      SB_PHISHING -> R.string.phishing
      else -> R.string.none

    }
    return getString(stringResource)

  }

  companion object {
    private const val SEARCH_URI_BASE = "https://duckduckgo.com/?q="
    private const val INITIAL_URL = "www.mozilla.org"
  }
}
