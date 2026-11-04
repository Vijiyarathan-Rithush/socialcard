package com.rvi.domain;

import com.rvi.domain.exception.NDefValueException;

public record NDefContact(String firstName, String lastName, String email, String phone, String website) implements NDefContent
{
    public NDefContact
    {
        firstName = optional(firstName);
        lastName = optional(lastName);
        email = optional(email);
        phone = optional(phone);
        website = optional(website);

        if (firstName.isBlank() && lastName.isBlank())
        {
            throw new NDefValueException("A contact name is required");
        }

        if (!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+"))
        {
            throw new NDefValueException("Invalid contact email");
        }

        if (!phone.isEmpty() && (!phone.matches("[+0-9() .-]+") || !phone.matches(".*[0-9].*")))
        {
            throw new NDefValueException("Invalid contact phone number");
        }

        if (!website.isEmpty())
        {
            website = new NDefUri(website).uri();

            if (!website.regionMatches(true, 0, "https://", 0, 8) && !website.regionMatches(true, 0, "http://", 0, 7))
            {
                throw new NDefValueException("Contact website must use HTTP or HTTPS");
            }
        }
    }

    private static String optional(final String value)
    {
        if (value == null)
        {
            return "";
        }

        return value.strip();
    }
}
