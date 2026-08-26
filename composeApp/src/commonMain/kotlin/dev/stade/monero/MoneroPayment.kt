package dev.stade.monero

private const val B58 = "1-9A-HJ-NP-Za-km-z"
private val MONERO_URI_REGEX = Regex("""monero:([$B58]{95}(?:[$B58]{11})?)(\?\S*)?""", RegexOption.IGNORE_CASE)
private val MONERO_ADDRESS_REGEX = Regex("""\b[48][$B58]{94}(?:[$B58]{11})?\b""")

data class MoneroPaymentRequest(
    val address: String,
    val amount: String? = null,
    val description: String? = null,
    val recipientName: String? = null
) {
    val paymentUri: String
        get() {
            val params = buildList {
                if (!amount.isNullOrBlank()) add("tx_amount=$amount")
                if (!recipientName.isNullOrBlank()) add("recipient_name=${encodeUriComponent(recipientName)}")
                if (!description.isNullOrBlank()) add("tx_description=${encodeUriComponent(description)}")
            }
            return if (params.isEmpty()) "monero:$address" else "monero:$address?${params.joinToString("&")}"
        }
}

fun extractMoneroPayment(text: String): MoneroPaymentRequest? {
    val uriMatch = MONERO_URI_REGEX.find(text)
    if (uriMatch != null) {
        val address = uriMatch.groupValues[1]
        val query = uriMatch.groupValues.getOrNull(2).orEmpty().removePrefix("?")
        val params = query.split("&").filter { it.isNotBlank() }.associate { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) pair to "" else pair.substring(0, idx) to decodeUriComponent(pair.substring(idx + 1))
        }
        return MoneroPaymentRequest(
            address = address,
            amount = params["tx_amount"]?.takeIf { it.isNotBlank() },
            description = params["tx_description"]?.takeIf { it.isNotBlank() },
            recipientName = params["recipient_name"]?.takeIf { it.isNotBlank() }
        )
    }
    val addrMatch = MONERO_ADDRESS_REGEX.find(text) ?: return null
    return MoneroPaymentRequest(address = addrMatch.value)
}

private fun encodeUriComponent(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = (b.toInt() and 0xff).toChar()
        if (c.isLetterOrDigit() || c in "-_.~") append(c)
        else append('%').append((b.toInt() and 0xff).toString(16).padStart(2, '0').uppercase())
    }
}

private fun decodeUriComponent(s: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '%' && i + 2 < s.length -> {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.add(hex.toByte())
                    i += 3
                } else {
                    bytes.add(c.code.toByte())
                    i += 1
                }
            }
            c == '+' -> {
                bytes.add(' '.code.toByte())
                i += 1
            }
            else -> {
                bytes.addAll(c.toString().encodeToByteArray().toList())
                i += 1
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

expect fun moneroQrMatrix(text: String): Array<BooleanArray>?
