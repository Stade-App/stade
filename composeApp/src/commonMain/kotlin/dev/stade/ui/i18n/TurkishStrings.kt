package dev.stade.ui.i18n

import dev.stade.security.SessionTimeout

object TurkishStrings : AppStrings() {
    override val back = "Geri"
    override val cancel = "İptal"
    override val delete = "Sil"

    override val loading = "Yükleniyor…"
    override val welcomeTitle = "Stade'e hoş geldin"
    override val welcomeDescription =
        "Uçtan uca şifreli, sunucusuz, post-quantum güvenli mesajlaşma.\n" +
        "Başlamak için bir takma ad seç — kalıcı bir Stade ID atanacak."
    override val nicknamePlaceholder = "Takma ad"
    override val createIdentity = "Kimlik oluştur"

    override val unlockTitle = "Kilidi aç"
    override val unlockSubtitle = "Devam etmek için şifreni gir."
    override val tooManyAttemptsSubtitle = "Çok fazla hatalı giriş. Bekleyin."
    override val forgotPin = "Şifremi unuttum"
    override val resetPinTitle = "Şifreyi sıfırla"
    override val resetPinBody =
        "Şifre cihazından kurtarılamaz. Devam edersen tüm yerel veriler kalıcı olarak silinir ve uygulama sıfırlanır."
    override val resetAndWipe = "Sıfırla ve sil"
    override val vaultNotInitialized = "Kasa başlatılmamış"
    override fun wrongPinRemaining(remaining: Int) = "Şifre hatalı ($remaining hak kaldı)"
    override val wrongPin = "Şifre hatalı"
    override val wiping = "Siliniyor…"
    override fun retryIn(formattedTime: String) = "Yeniden denemek için $formattedTime"
    override fun formatRemainingTime(seconds: Long) = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}dk ${seconds % 60}s"
        else -> "${seconds / 3600}sa ${(seconds % 3600) / 60}dk"
    }

    override val enterCurrentPinTitle = "Mevcut şifreyi gir"
    override val setNewPinTitle = "Yeni şifre belirle"
    override val confirmPinTitle = "Şifreyi doğrula"
    override val enterCurrentPinSubtitle = "Devam etmek için mevcut şifreni gir."
    override fun setPinSubtitle(min: Int, max: Int) = "$min-$max haneli bir şifre belirle."
    override val confirmPinSubtitle = "Aynı şifreyi tekrar gir."
    override val wrongCurrentPin = "Mevcut şifre hatalı"
    override val pinMismatch = "Şifreler eşleşmiyor"
    override val pinChangeFailed = "Şifre değiştirilemedi"
    override val confirmAction = "Onayla"
    override val backspaceAction = "Sil"

    override val appTitle = "Stade"
    override val searchContactsPlaceholder = "Kişi ara…"
    override val closeSearch = "Aramayı kapat"
    override val searchAction = "Ara"
    override val settingsAction = "Ayarlar"
    override val addContactAction = "Kişi ekle"
    override val noContactsTitle = "Henüz kişin yok"
    override val noContactsHint = "Yeni bir kişi eklemek için sağ alttaki butona dokun."
    override val noSearchResults = "Eşleşen kişi yok"
    override val showVerificationCode = "Doğrulama kodunu göster"
    override val deleteContact = "Kişiyi sil"
    override val pinChatAction = "Sohbeti sabitle"
    override val unpinChatAction = "Sabitlemeyi kaldır"
    override val noMessages = "Henüz mesaj yok"
    override fun deleteContactTitle(name: String) = "\"$name\" silinsin mi?"
    override val deleteContactBody =
        "Tüm mesajlar, bekleyen kayıtlar ve şifreleme anahtarları kalıcı olarak silinecek. " +
        "Yeniden konuşmak için her iki tarafın da kişiyi silip baştan eklemesi gerekir.\n\nBu işlem geri alınamaz."

    override val online = "çevrimiçi"
    override val offline = "çevrimdışı"
    override val deleteContactDialogTitle = "Kişiyi sil?"
    override fun deleteContactDialogBody(name: String) =
        "\"$name\" kişisi, tüm mesajlar, bekleyen kuyruk kayıtları ve şifreleme anahtarları (ratchet) tamamen silinecek. " +
        "Aynı kişiyle yeniden konuşmak için her iki tarafın da kişiyi silip yeni davet linkiyle baştan eklemesi gerekir.\n\nBu işlem geri alınamaz."
    override val noMessagesYet = "Henüz mesaj yok"
    override val sendFirstMessage = "İlk mesajı sen gönder."
    override val typeMessagePlaceholder = "Mesaj yaz…"
    override val sendButton = "Gönder"
    override val verifyAction = "Doğrula"
    override val deleteContactIconDescription = "Kişiyi sil"
    override val connectionFailed = "Bağlantı kurulamadı"
    override val collapseAction = "Daralt"
    override val expandAction = "Genişlet"
    override val noConnectionInfo =
        "Bu kişinin kayıtlı bağlantı bilgisi yok. Karşı taraftan yeni bir davet kodu iste ve aşağıya yapıştır."
    override val connectionChannels = "Bağlantı kanalları"
    override val trying = "deneniyor"
    override val channelReadyVerifying = "kanal hazır, doğrulama…"
    override val connectedLabel = "bağlı"
    override val unreachable = "ulaşılamıyor"
    override val handshakeFailed = "doğrulama başarısız"
    override val notYetTried = "henüz denenmedi"
    override fun channelLabel(index: Int, maskedAddr: String) = "Kanal #$index • $maskedAddr"
    override val connectionDelayNote =
        "Bağlantı uzaktan kuruluyorsa karşı tarafın çevrimiçi olması ve ağ kanallarının hazır olması birkaç dakika sürebilir."
    override val newInviteCodeLabel = "Yeni davet kodu"
    override val applyInviteCode = "Davet kodunu uygula"
    override val clearAddresses = "Temizle"
    override fun handshakeRejected(reason: String) = "Bağlantı reddedildi: $reason"
    override val contactConnected = "Bağlandı ✓"
    override val decryptFailed =
        "Mesaj şifresi çözülemedi — kişiyi her iki tarafta da silip yeniden ekleyin"
    override fun sendFailed(reason: String) = "Mesaj gönderilemedi: $reason"
    override val invalidInvite = "Davet kodu geçersiz"
    override val inviteBelongsToDifferent = "Bu davet başka bir kişiye ait"
    override val noConnectionInInvite = "Davette bağlantı bilgisi yok"
    override val connectionInfoUpdated = "Bağlantı bilgileri güncellendi"
    override val addressesCleared = "Adresler temizlendi"
    override fun diagnosticError(msg: String) = "Hata: $msg"

    override val dialQueued = "kuyrukta…"
    override val dialTransportNotReady = "taşıma hazır değil — bekleniyor"
    override fun dialTransportStarting(msg: String) = "taşıma başlatılıyor ($msg)…"
    override fun dialConnectingVia(transport: String, attempt: Int) =
        "$transport üzerinden bağlanılıyor (deneme #$attempt)…"
    override fun dialConnectFailedRetry(err: String) = "$err — yeniden denenecek"
    override val dialHandshaking = "handshake yapılıyor…"
    override val dialConnectedOk = "bağlandı ✓"
    override val dialHandshakeFailedRetry = "handshake başarısız — yeniden denenecek"
    override val dialOwnStaleAddress = "bu kendi adresin (bayat) — güncellenmeli"
    override val dialTransportClosed = "taşıma kapalı"
    override val dialUnreachableTimeout = "ulaşılamadı / zaman aşımı"
    override val dialHandshakeFailed = "handshake başarısız"

    override fun hsProtocolMismatch(peer: Int, app: Int) =
        "Protokol uyumsuz: v$peer (uygulama v$app)"
    override val hsKeySizeBad = "Anahtar boyutları hatalı"
    override val hsSelfConnected = "Kendine bağlandın (bayat adres)"
    override val hsStadeIdMismatch = "Stade ID anahtarlarla eşleşmiyor"
    override val hsTranscriptMismatch = "Transcript commitment uyumsuz (downgrade?)"
    override val hsAuthStadeIdMismatch = "AUTH Stade ID HELLO ile eşleşmiyor"
    override val hsSignaturesInvalid = "İmzalar doğrulanamadı"
    override val hsEdInvalid = "Ed25519 imzası geçersiz"
    override val hsMldsaInvalid = "ML-DSA imzası geçersiz (post-quantum doğrulama başarısız)"
    override val hsMlkemDecapFailed = "ML-KEM decapsulate başarısız"
    override val unknownNickname = "Bilinmeyen"
    override fun contactNameFallback(last4: String) = "Kişi-$last4"

    override val backgroundRunningNotice = "Uygulama arka planda çalışmaya devam ediyor"
    override val trayOpen = "Aç"
    override val trayExit = "Çıkış"

    override val copiedLabel = "Kopyalandı"
    override fun addrRemoteNetwork(first: String, last: String) = "Uzak ağ • $first…$last"
    override val addrLocalNetwork = "Yerel ağ"
    override val addrNetwork = "Ağ"
    override fun timeYesterday(time: String) = "dün $time"

    override val saveMediaDialogTitle = "Medyayı kaydet"

    override val vaultMetaUnreadable = "Meta okunamadı"
    override val vaultKeyDerivationFailed = "Anahtar türetilemedi"
    override val vaultDekDecryptFailed = "DEK çözülemedi"

    override val notifConnectionChannelName = "Bağlantı"
    override val notifConnectionChannelDesc = "Stade eşlerle bağlantı durumunu korur"
    override val notifMessagesChannelName = "Mesajlar"
    override val notifMessagesChannelDesc = "Gelen şifreli mesajlar için bildirimler"
    override val notifRunningTitle = "Stade çalışıyor"
    override val notifRunningText = "Eşler arası bağlantılar etkin"
    override fun notifNewMessages(count: Int) = "$count yeni mesajınız var"
    override val notifNewMessageFallback = "Yeni mesaj"
    override val notifReminderChannelName = "Hatırlatmalar"
    override val notifReminderChannelDesc = "Mesaj alabilmen için Stade'yi açmanı hatırlatır"
    override val notifBootReminderTitle = "Stade"
    override val notifBootReminderText = "Stade'de aktif kalmak için giriş yap"

    override val addContactTitle = "Kişi ekle"
    override val step1Title = "Kendi davetini paylaş"
    override val step1Description =
        "Bağlantı kurmak için aşağıdaki butona basıp davet kodunu paylaş. " +
        "Stade ID sadece kimlik etiketidir — davet kodunun yerine geçmez."
    override fun copyInviteCode(length: Int) = "Davet kodunu kopyala ($length karakter)"
    override fun inviteCodeCopied(length: Int) =
        "Davet kodu kopyalandı ($length karakter) — karşı tarafa gönder"
    override val yourStadeId = "Stade kimliğin:"
    override val step2Title = "Karşı tarafın davetini gir"
    override val inviteCodeLabel = "Davet kodu"
    override fun charCount(n: Int) = "$n karakter (sağlam bir davet ~10500 karakter olmalı)"
    override val contactNameLabel = "Bu kişi için isim"
    override val acceptInvite = "Daveti kabul et"
    override val pendingInviteOpened =
        "Davet dosyası açıldı — bu kişiye bir isim ver ve \"Daveti kabul et\""
    override val inviteCodeIsStadeId =
        "Bu bir Stade ID (kimlik etiketi), davet kodu değil. Karşı tarafın 'Davet kodunu kopyala' " +
        "butonuna basıp gönderdiği uzun bloğu (STADE2-… ~10500 karakter) yapıştır."
    override fun inviteMissingPrefix(first: String) =
        "Davet 'STADE2-' ile başlamıyor (ilk: '$first')"
    override fun inviteTooShort(actual: Int, expected: Int) =
        "Davet kodu eksik: $actual bayt, $expected gerekli — kopyalarken karakter düşmüş olabilir"
    override fun inviteTrailingBytes(extra: Int) =
        "Davet kodu fazla $extra bayt içeriyor — yanlışlıkla iki kez yapıştırılmış olabilir"
    override val inviteBadMagic = "Davet kodu Stade formatında değil (magic uyuşmuyor)"
    override fun inviteBadVersion(version: Int) =
        "Davet kodu sürümü desteklenmiyor (v=$version)"
    override fun inviteBadNickname(length: Int) =
        "Davet kodu bozuk (nickname uzunluk=$length)"
    override fun inviteBadAddressBlob(length: Int) =
        "Davet kodu bozuk (adres bloğu uzunluk=$length)"
    override val inviteEdVerifyFail =
        "Davet kodu imzası geçersiz (Ed25519) — kopyalama sırasında bozulmuş olabilir"
    override val inviteMlDsaVerifyFail =
        "Davet kodu PQ imzası geçersiz (ML-DSA) — kopyalama sırasında bozulmuş olabilir"
    override fun inviteDecodeError(cause: String) = "Base32 çözme hatası: $cause"
    override val selfInviteError = "Bu senin kendi davetin"
    override fun alreadyAdded(stadeId: String) = "Bu kişi zaten ekli ($stadeId)"
    override val inviteAcceptedNoAddr =
        "Davet kabul edildi — karşı tarafın çevrimiçi olmasını bekle"
    override fun inviteAccepted(name: String, count: Int) =
        "Davet kabul edildi ($name) — bağlanılıyor… ($count adres deneniyor; karşı taraf çevrimiçi olmalı)"
    override fun contactAdded(name: String) = "✓ $name eklendi — sohbete başlayabilirsin"
    override val connectionTimeout =
        "Bağlanılamadı — karşı taraf çevrimdışı olabilir veya Tor/ağ erişimi yok. " +
        "Uygulamayı açık bırak; bağlantı kurulunca kişi otomatik eklenir."
    override val torStartingInviteHint =
        "Tor henüz başlatılıyor — bu davet kodunu paylaşmadan önce tam olarak hazır olmasını bekle."
    override val inviteLanOnlyWarning =
        "Bu davet yalnızca yerel ağ (Wi-Fi) adresi içeriyor — sadece aynı ağda bağlanabilir. " +
        "Uzaktaki birine ulaşmak için, karşı taraf Tor hazır olduktan sonra oluşturduğu bir davet paylaşmalı."
    override val inviteNotReadyForRemote =
        "Tor henüz başlatılıyor, bu yüzden davetin uzaktan erişilebilir değil. " +
        "Hazır olmasını bekle; aksi halde karşı taraf yalnızca aynı Wi-Fi'da bağlanabilir."
    override val addContactDialogTitle = "Kişi eklensin mi?"
    override fun addContactQuestion(name: String) = "\"$name\" kişisini eklemek istiyor musun?"
    override val incomingInviteMessage = "Bu kullanıcıyı eklemek üzeresin."
    override val confirmAddCheckbox = "Bu kişiyi eklemek istediğimi onaylıyorum"
    override val addAction = "Ekle"
    override val notNowAction = "Şimdi değil"
    override fun connectingInBackground(name: String) =
        "$name ile bağlantı kuruluyor… bu pencereyi kapatabilirsin; bağlanınca kişi listene eklenir."
    override fun error(msg: String) = "Hata: $msg"

    override val settingsTitle = "Ayarlar"
    override val identitySection = "Kimlik"
    override val appearanceSection = "Görünüm"
    override val dynamicColorTitle = "Dinamik renk"
    override val dynamicColorSubtitle = "Material You duvar kağıdı renklerini kullan"
    override val notificationsSection = "Bildirimler"
    override val messageNotificationsTitle = "Mesaj bildirimleri"
    override val notificationsOnSubtitle = "Yeni mesajlarda bildirim gönder"
    override val notificationsOffSubtitle = "Bildirimler kapalı"
    override val hideNotificationTitle = "Bildirim içeriğini gizle"
    override val hiddenNotificationSubtitle = "\"X yeni mesajınız var\" şeklinde gösterilir"
    override val visibleNotificationSubtitle = "Gönderici adı ve mesaj önizlenebilir"
    override val systemNotificationsTitle = "Sistem bildirim ayarları"
    override val systemNotificationsSubtitle = "Ses, titreşim ve kanal ayarları"
    override val runInBackgroundTitle = "Arka planda çalış"
    override val runInBackgroundOnSubtitle = "Pencere kapatıldığında uygulama tepside kalır"
    override val runInBackgroundOffSubtitle = "Pencere kapatılınca uygulama tamamen kapanır"
    override val networkSection = "Ağ Bağlantısı"
    override val transportLayersTitle = "Taşıma katmanları"
    override val transportLayersSubtitle = "LAN, Tor ve diğer ağ ayarları"
    override val securitySection = "Güvenlik"
    override val securitySettingsTitle = "Güvenlik ayarları"
    override val securitySettingsSubtitle = "Şifre, otomatik kilit ve diğerleri."
    override val aboutSection = "Hakkında"
    override val aboutTitle = "Hakkında"
    override val aboutSubtitle = "Uygulama bilgisi ve sosyal medya"
    override val aboutAppDescription = "Uçtan uca şifreli, gizli mesajlaşma."
    override val aboutFollowUs = "Bizi takip edin"
    override val aboutLinkComingSoon = "Yakında"
    override val aboutVersionLabel = "Sürüm"
    override val accountSection = "Hesap"
    override val logoutTitle = "Oturumu kapat"
    override val logoutSubtitle = "Yerel veriler korunur"
    override val localIdentity = "Yerel kimlik"
    override val fingerprintLabel = "Kimlik parmak izi"
    override val fingerprintCopied = "Kopyalandı!"
    override val copyButton = "Kopyala"
    override val logoutDialogTitle = "Oturumu Kapat"
    override val logoutDialogBody =
        "Bu cihazdaki kimliğin, kişilerin, sohbet geçmişin ve taşıma ayarların kalıcı olarak silinir. Bu işlem geri alınamaz."
    override val deleteAndLogout = "Sil ve çıkış yap"
    override val languageSection = "Dil"
    override val languageTitle = "Dil"
    override val languageSubtitle = "Türkçe"

    override val pinSection = "Şifre"
    override val changePinTitle = "Şifreyi değiştir"
    override val changePinSubtitle = "Mevcut şifreyi doğrulayıp yeni bir şifre belirleyin"
    override val scrambleKeypadTitle = "Tuş takımını karıştır"
    override val scrambleKeypadOnSubtitle = "Her girişte rakamlar rastgele sıralanır"
    override val scrambleKeypadOffSubtitle = "Rakamlar standart sırada gösterilir"
    override val sessionSection = "Oturum"
    override val autoLockTitle = "Otomatik kilit"
    override fun autoLockSubtitle(label: String) = "Arka plana geçildikten sonra: $label"
    override fun sessionTimeoutLabel(seconds: Int) = when (seconds) {
        SessionTimeout.IMMEDIATE -> "Hemen"
        SessionTimeout.NEVER -> "Asla"
        30 -> "30 saniye"
        60 -> "1 dakika"
        5 * 60 -> "5 dakika"
        15 * 60 -> "15 dakika"
        60 * 60 -> "1 saat"
        else -> if (seconds < 60) "$seconds saniye" else "${seconds / 60} dakika"
    }

    override val privacySection = "Gizlilik"
    override val screenshotBlockingTitle = "Ekran görüntüsü almayı devre dışı bırak"
    override val screenshotBlockingOnSubtitle = "Uygulama içeriği son uygulamalar'da gizlenir; ekran görüntüsü alınamaz"
    override val screenshotBlockingOffSubtitle = "Uygulama içeriği son uygulamalar'da görünür"

    override val autoLockNeverInfoTitle = "«Asla» Seçeneği Hakkında"
    override val autoLockNeverInfoBody =
        "«Asla» seçildiğinde, uygulama arka planda çalışırken öne alındığında PIN istenmez.\n\n" +
        "Ancak uygulama tamamen kapatılıp yeniden açıldığında — sistem tarafından bellekten temizlenmiş olsa dahi — şifrenizi girmeniz gerekir."
    override val understood = "Anladım"

    override val transportsTitle = "Taşıma katmanları"
    override val notRegistered = "kayıtlı değil"
    override fun transportRunning(msg: String) = "çalışıyor · $msg"
    override val transportReady = "hazır"
    override fun transportUnavailable(msg: String) = "uygun değil · $msg"
    override fun transportStatus(addr: String) = "Durum: $addr hazır"
    override fun transportChannelsReady(n: Int) = "$n kanal hazır"
    override val torBuiltinNote =
        "Tor kurulumu otomatik olarak yapılır ve başlatılır. Yüklemenin tamamlanmasını bekledikten sonra sorunsuzca kullanabilirsiniz. Bu işlem tek seferliktir."
    override val hiddenServiceDescription =
        "Hidden Service (gelişmiş — isteğe bağlı). Bağlantı kanalı kurmak için torrc'de uygun yapılandırmayı " +
        "kurmanız ve elde edilen kimliği aşağıdaki alana girmeniz gerekir. Boş bırakırsanız bu kanal devre dışı kalır."
    override val hiddenServiceId = "Hidden service kimliği"
    override val onionVirtport = "Port (onion VIRTPORT)"
    override val localPortLabel = "Yerel port (boşsa = Port)"
    override val socks5Note =
        "SOCKS5 (giden bağlantı için). Standart Tor: 9050. Tor Browser: 9150. Orbot: 9050. " +
        "Bu çalışmazsa sistemde Tor yüklü değil veya farklı portta dinliyor demektir."
    override val socks5Host = "SOCKS5 host"
    override val socks5Port = "SOCKS5 port"
    override val saveAndRestart = "Kaydet ve kanalı yeniden başlat"
    override val lanLabel = "Yerel ağ"
    override val torLabel = "Uzak ağ kanalı"

    override val useBridgesTitle = "Köprü kullan"
    override val useBridgesHint = "Tor'un engellendiği veya sansürlendiği yerlerde bağlanmaya yardımcı olur"
    override val useBuiltInBridgesTitle = "Yerleşik obfs4 köprülerini kullan"
    override val customBridgesLabel = "Özel köprü satırları"
    override val customBridgesHint = "bridges.torproject.org adresinden veya güvendiğin bir kişiden aldığın köprü satırlarını her satıra bir tane olacak şekilde yapıştır (isteğe bağlı, yerleşik köprülere ek veya onların yerine)"
    override val bridgesNotSupportedNote = "Köprü desteği bu platformda henüz mevcut değil."

    override val verifyContactTitle = "Kişiyi doğrula"
    override val safetyNumber = "Güvenlik numarası"
    override val safetyNumberNote =
        "Bu numarayı yüz yüze veya başka bir güvenli kanaldan karşılaştır."
    override val markAsVerified = "Doğrulandı olarak işaretle"
    override val alreadyVerifiedLabel = "Doğrulandı ✓"
    override val verifiedLabel = "Doğrulandı"
    override val contactStadeId = "Karşı tarafın Stade ID'si"

    override val selectContactHint = "Yeni bir sohbete başlamak için sol panelden bir kişi seç."

    override val attachPhoto = "Fotoğraf ekle"
    override val selectMediaTitle = "Medya Seç"
    override val photoMessage = "📷 Fotoğraf"
    override val photoSendFailed = "Fotoğraf yüklenemedi"
    override val photoTooBig = "Fotoğraf çok büyük (maks. 3 MB)"
    override val tapToViewPhoto = "Görüntülemek için dokun"
    override val closePhoto = "Kapat"
    override val removeAttachment = "Kaldır"
    override fun attachmentCount(count: Int) = if (count == 1) "1 fotoğraf eklendi" else "$count fotoğraf eklendi"

    override val recordVoice = "Sesli mesaj kaydet"
    override val stopRecording = "Kaydı durdur"
    override val voiceMessage = "🎤 Sesli mesaj"
    override val voiceSendFailed = "Sesli mesaj gönderilemedi"
    override val micPermissionDenied = "Sesli mesaj kaydetmek için mikrofon izni gerekiyor"
    override val voiceMaxDurationReached = "Maksimum sesli mesaj uzunluğuna ulaşıldı"

    override val createGroupTitle = "Grup Oluştur"
    override val createGroupAction = "Grup Oluştur"
    override val groupNameLabel = "Grup adı"
    override val selectMembersHint = "Gruba eklenecek üyeleri seç:"
    override val groupInviteTitle = "Grup Davet Linki"
    override val groupInviteBody = "Bu linki gruba davet etmek istediğin kişiyle paylaş. Kişinin zaten rehberinde kayıtlı olması gerekir."
    override val copyInviteLink = "Linki Kopyala"
    override val deleteGroupTitle = "Grubu Sil"
    override val deleteGroupBody = "Tüm grup mesajları ve üye kayıtları kalıcı olarak silinecek. Bu işlem geri alınamaz."
    override val groupGenerateInvite = "Davet linki oluştur"
    override fun groupMemberCount(count: Int) = "$count üye"

    override val addMembersTitle = "Gruba kişi ekle"
    override val addMembersAction = "Ekle"
    override val addMembersHint = "Eklenecek kişileri seç:"
    override val noContactsToAdd = "Eklenebilecek kişi yok. Bütün kişilerin grupta zaten kayıtlı görünüyor."
    override fun membersAdded(count: Int) = if (count == 1) "1 kişi davet edildi" else "$count kişi davet edildi"
    override val leaveGroupAction = "Gruptan ayrıl"
    override val leaveGroupTitle = "Gruptan ayrılınsın mı?"
    override val leaveGroupBody = "Bu grup yerel cihazından kaldırılacak. Diğer üyeler grupta kalmaya devam eder. Bu işlem geri alınamaz."
    override val leaveAction = "Ayrıl"

    override val viewMembersAction = "Üyeleri görüntüle"
    override val groupMembersTitle = "Grup üyeleri"
    override val youLabel = "Sen"
    override val groupAdminBadge = "Yönetici"
    override val kickMemberAction = "Gruptan çıkar"
    override fun kickMemberTitle(name: String) = "$name çıkarılsın mı?"
    override val kickMemberBody = "Bu kişi artık bu grupta mesaj gönderemez veya alamaz. Bu işlem geri alınamaz."
    override fun memberKicked(name: String) = "$name gruptan çıkarıldı"
    override val kickMemberFailed = "Üye çıkarılamadı"
    override fun removedFromGroupNotification(groupName: String) = "$groupName grubundan çıkarıldın"

    override val copyMessage = "Mesajı kopyala"
    override val deleteMessageForMe = "Kendinden sil"
    override val deleteMessagesForMe = "Seçili mesajları sil"
    override fun selectedCount(count: Int) = "$count seçili"
    override val messageCopied = "Mesaj kopyalandı"
    override val cancelSelection = "Seçimi iptal et"

    override val saveImageAction = "Kaydet"
    override val copyImageAction = "Kopyala"
    override val imageSaved = "Görsel kaydedildi"
    override val imageSaveFailed = "Görsel kaydedilemedi"
    override val imageCopied = "Görsel kopyalandı"
    override val imageCopyFailed = "Görsel kopyalanamadı"
}

