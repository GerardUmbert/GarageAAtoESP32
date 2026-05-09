#include <unity.h>
#include <string.h>
#include <stdint.h>
#include "mbedtls/md.h"

// Minimal standalone HMAC-SHA256 for the test environment
static void hmac_sha256(const uint8_t *key, size_t key_len,
                        const uint8_t *data, size_t data_len,
                        uint8_t *out) {
    mbedtls_md_context_t ctx;
    const mbedtls_md_info_t *info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    mbedtls_md_init(&ctx);
    mbedtls_md_setup(&ctx, info, 1);
    mbedtls_md_hmac_starts(&ctx, key, key_len);
    mbedtls_md_hmac_update(&ctx, data, data_len);
    mbedtls_md_hmac_finish(&ctx, out);
    mbedtls_md_free(&ctx);
}

// Known test vector: generated with Python
// import hmac, hashlib
// hmac.new(b"test-pin", b"\x01\x02\x03\x04\x05\x06\x07\x08"
//                        b"\x09\x0a\x0b\x0c\x0d\x0e\x0f\x10",
//          hashlib.sha256).hexdigest()
// => "7e8fb3a6..."  (see expected[] below)
static const uint8_t TEST_KEY[]   = { 't','e','s','t','-','p','i','n' };
static const uint8_t TEST_NONCE[] = {
    0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
    0x09,0x0a,0x0b,0x0c,0x0d,0x0e,0x0f,0x10
};
static const uint8_t EXPECTED[32] = {
    0x7e,0x8f,0xb3,0xa6,0x15,0x3d,0x9b,0x44,
    0x44,0xdb,0x61,0xf0,0x04,0x75,0x28,0xb0,
    0x9c,0x5e,0xbf,0x66,0x88,0x6c,0x07,0xf3,
    0x03,0x6b,0xac,0x16,0x01,0xad,0x63,0x21
};

void test_hmac_known_vector() {
    uint8_t result[32];
    hmac_sha256(TEST_KEY, sizeof(TEST_KEY),
                TEST_NONCE, sizeof(TEST_NONCE),
                result);
    TEST_ASSERT_EQUAL_UINT8_ARRAY(EXPECTED, result, 32);
}

void test_hmac_wrong_key_fails() {
    const uint8_t wrong_key[] = { 'w','r','o','n','g' };
    uint8_t result[32];
    hmac_sha256(wrong_key, sizeof(wrong_key),
                TEST_NONCE, sizeof(TEST_NONCE),
                result);
    TEST_ASSERT_FALSE(memcmp(EXPECTED, result, 32) == 0);
}

void test_hmac_wrong_nonce_fails() {
    uint8_t bad_nonce[16];
    memcpy(bad_nonce, TEST_NONCE, 16);
    bad_nonce[0] ^= 0xFF;  // Flip one byte
    uint8_t result[32];
    hmac_sha256(TEST_KEY, sizeof(TEST_KEY),
                bad_nonce, sizeof(bad_nonce),
                result);
    TEST_ASSERT_FALSE(memcmp(EXPECTED, result, 32) == 0);
}

int main(int argc, char **argv) {
    UNITY_BEGIN();
    RUN_TEST(test_hmac_known_vector);
    RUN_TEST(test_hmac_wrong_key_fails);
    RUN_TEST(test_hmac_wrong_nonce_fails);
    return UNITY_END();
}
