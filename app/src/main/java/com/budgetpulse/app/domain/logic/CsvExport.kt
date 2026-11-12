package com.budgetpulse.app.domain.logic

import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExport {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    fun build(txs: List<Transaction>, accountNames: Map<Long, String>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder("date,type,category,amount,account,note\n")
        txs.sortedBy { it.timestamp }.forEach { t ->
            sb.append(FORMAT.format(Instant.ofEpochMilli(t.timestamp).atZone(zone))).append(',')
                .append(if (t.type == TxType.INCOME) "income" else "expense").append(',')
                .append(escape(t.category.label)).append(',')
                .append(t.amount).append(',')
                .append(escape(accountNames[t.accountId] ?: "")).append(',')
                .append(escape(t.note)).append('\n')
        }
        return sb.toString()
    }
}
