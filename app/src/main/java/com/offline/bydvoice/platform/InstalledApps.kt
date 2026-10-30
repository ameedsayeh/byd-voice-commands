package com.offline.bydvoice.platform

import android.content.Context
import android.content.Intent
import com.offline.bydvoice.nlu.AppDirectory
import com.offline.bydvoice.nlu.Fuzzy
import com.offline.bydvoice.nlu.Text

/** "open <app>": fuzzy-matches the spoken name against launchable apps' labels. */
class InstalledApps(private val context: Context) : AppDirectory {

    private val apps: List<Pair<String, String>> by lazy {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            .filter { it.first != context.packageName }
            .distinctBy { it.first }
    }

    override fun find(spokenName: String): Pair<String, String>? {
        val q = Text.normalize(spokenName)
        val qTokens = q.split(' ').filter { it.length > 1 }
        if (qTokens.isEmpty()) return null
        return apps.map { (pkg, label) ->
            val l = Text.normalize(label)
            val lTokens = l.split(' ')
            val whole = Fuzzy.similarity(q, l)
            val tokenHits = qTokens.count { qt -> lTokens.any { lt -> lt == qt || Fuzzy.match(qt, lt) > 0 } }.toDouble() / qTokens.size
            Triple(pkg, label, maxOf(whole, tokenHits * 0.95))
        }.filter { it.third >= 0.72 }.maxByOrNull { it.third }?.let { it.first to it.second }
    }
}
