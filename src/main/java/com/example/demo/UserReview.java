package com.example.demo;

public class UserReview {

    private String comment;
    private int rating;
    private String username;

    public UserReview() {
        
    }

    public String getComment() {
        return comment;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}