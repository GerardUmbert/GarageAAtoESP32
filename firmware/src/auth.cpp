#include "auth.h"
#include "config.h"
#include <string.h>
#include "mbedtls/md.h"

namespace Auth {

void hmac_sha256(const uint8_t *key, size_t key_len,
                 const uint8_t *data, size_t data_len,
                 uint8_t *buf) {
    mbedtls_md_context_t ctx;
    const mbedtls_md_info_t *info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    mbedtls_md_init(&ctx);
    mbedtls_md_setup(&ctx, info, 1 /* use HMAC */);
    mbedtls_md_hmac_starts(&ctx, key, key_len);
    mbedtls_md_hmac_update(&ctx, data, data_len);
    mbedtls_md_hmac_finish(&ctx, buf);
    mbedtls_md_free(&ctx);
}

bool verify(const uint8_t *nonce, size_t nonce_len,
            const uint8_t *provided, size_t provided_len) {
    if (provided_len != 32) return false;

    const uint8_t *pin = reinterpret_cast<const uint8_t *>(USER_PIN);
    size_t pin_len = strlen(USER_PIN);

    uint8_t expected[32];
    hmac_sha256(pin, pin_len, nonce, nonce_len, expected);

    // Constant-time comparison to prevent timing attacks
    uint8_t diff = 0;
    for (size_t i = 0; i < 32; i++) {
        diff |= expected[i] ^ provided[i];
    }
    return diff == 0;
}

} // namespace Auth
