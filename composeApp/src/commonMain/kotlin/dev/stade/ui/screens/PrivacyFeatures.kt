package dev.stade.ui.screens

/**
 * Platforma özgü gizlilik özelliklerinin desteklenip desteklenmediğini bildirir.
 *
 * Android: FLAG_SECURE gibi pencere düzeyinde ekran koruma özelliklerini destekler → true
 * Desktop: Bu özellikler pencere sistemi tarafından sağlanmadığından desteklenmez → false
 *
 * İleride masaüstüne özgü gizlilik ayarları eklendikçe bu değer veya ek expect tanımları
 * bu dosyaya eklenmelidir.
 */
expect val isScreenPrivacySupported: Boolean

/**
 *
 * PIN tuş takımını gizlemek için var
 * Masaüstü kullanıcıları için PIN tuş takımının gizlenmesini sağlıyor.
 */
expect val isKeypadSupported: Boolean
