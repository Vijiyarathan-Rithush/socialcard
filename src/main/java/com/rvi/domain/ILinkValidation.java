package com.rvi.domain;

import java.util.Objects;

/** Storage usage includes the Type 2 TLV wrapper and page alignment. */
public sealed interface ILinkValidation
{
    record Valid(int requiredBytes, int ndefBytes) implements ILinkValidation
    {
        public Valid
        {
            if (ndefBytes <= 0 || requiredBytes < ndefBytes)
            {
                throw new IllegalArgumentException("Invalid message size.");
            }
        }
    }

    record Invalid(String message) implements ILinkValidation
    {
        public Invalid
        {
            Objects.requireNonNull(message, "Validation message is required.");
        }
    }
}
