#pragma once
#include <stdint.h>
#include <stddef.h>

namespace Auth {
    // Fills buf with HMAC-SHA256(key, data). buf must be 32 bytes.
    void hmac_sha256(const uint8_t *key, size_t key_len,
                     const uint8_t *data, size_t data_len,
                     uint8_t *buf);

    // Returns true if provided equals HMAC-SHA256(USER_PIN, nonce).
    bool verify(const uint8_t *nonce, size_t nonce_len,
                const uint8_t *provided, size_t provided_len);
}
