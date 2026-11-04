package com.rvi.application.creator;

import com.rvi.domain.NDefContact;

public final class NDefContactCreator
{
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String website;

    /** Sets the optional first name; at least one name is required at build time. */
    public NDefContactCreator firstName(final String firstName)
    {
        this.firstName = firstName;
        return this;
    }

    /** Sets the optional last name; at least one name is required at build time. */
    public NDefContactCreator lastName(final String lastName)
    {
        this.lastName = lastName;
        return this;
    }

    /** Sets an optional contact email; null becomes empty. */
    public NDefContactCreator email(final String email)
    {
        this.email = email;
        return this;
    }

    /** Sets an optional phone number; null becomes empty. */
    public NDefContactCreator phone(final String phone)
    {
        this.phone = phone;
        return this;
    }

    /** Sets an optional HTTP or HTTPS contact website; null becomes empty. */
    public NDefContactCreator website(final String website)
    {
        this.website = website;
        return this;
    }

    /** Validates collected values and returns a new immutable value. Does not access hardware. */
    public NDefContact build()
    {
        return new NDefContact(firstName, lastName, email, phone, website);
    }
}
