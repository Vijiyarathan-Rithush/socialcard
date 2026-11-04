package com.rvi.application.creator;

import com.rvi.domain.NDefContact;

public final class NDefContactCreator
{
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String website;

    public NDefContactCreator firstName(final String firstName)
    {
        this.firstName = firstName;
        return this;
    }

    public NDefContactCreator lastName(final String lastName)
    {
        this.lastName = lastName;
        return this;
    }

    public NDefContactCreator email(final String email)
    {
        this.email = email;
        return this;
    }

    public NDefContactCreator phone(final String phone)
    {
        this.phone = phone;
        return this;
    }

    public NDefContactCreator website(final String website)
    {
        this.website = website;
        return this;
    }

    public NDefContact build()
    {
        return new NDefContact(firstName, lastName, email, phone, website);
    }
}
