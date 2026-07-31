package com.geotagcamera.geotagginglocationonphoto.security

import com.geotagcamera.geotagginglocationonphoto.exif.SignedPayload

/**
 * Three real answers plus an I/O failure, as a sealed type rather than a
 * boolean — because "no proof" is NOT the same as "failed" (design section 05).
 * A photo with no embedded proof is shown neutral grey, not tamper-red: it was
 * simply never signed by this app, which is not a verdict against it.
 */
sealed interface VerificationOutcome {
    /** Hash matches and the signature verifies under the embedded public key. */
    data class Untampered(val payload: SignedPayload) : VerificationOutcome

    /** A proof is present but the image no longer matches it — edited since capture. */
    data class Edited(val payload: SignedPayload) : VerificationOutcome

    /** No GeoTag proof found in EXIF or XMP — neutral, not a failure. */
    data object NoProof : VerificationOutcome

    /** The file couldn't be opened or read at all. */
    data object Unreadable : VerificationOutcome
}
