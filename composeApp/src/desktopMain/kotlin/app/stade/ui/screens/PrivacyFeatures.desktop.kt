package app.stade.ui.screens

// Masaüstünde FLAG_SECURE ve benzeri pencere gizlilik özellikleri desteklenmez.
// İleride masaüstüne özgü gizlilik ayarları eklendiğinde bu değer true yapılabilir.
actual val isScreenPrivacySupported: Boolean = false

// Masaüstü kullanıcıları için PIN tuş takımının gizlenmesini sağlıyor.
actual val isKeypadSupported: Boolean = false
