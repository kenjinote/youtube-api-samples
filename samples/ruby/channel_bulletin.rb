#!/usr/bin/ruby

require 'rubygems'
require 'google/api_client'
# oauth/oauth_util is not part of the official Ruby client library. 
# Get it at http://samples.google-api-ruby-client.googlecode.com/git/oauth/oauth_util.rb
require 'oauth/oauth_util'
require 'trollop'


# An OAuth 2 access scope that allows for full read/write access.
YOUTUBE_READONLY_SCOPE = 'https://www.googleapis.com/auth/youtube'
YOUTUBE_API_SERVICE_NAME = 'youtube'
YOUTUBE_API_VERSION = 'v3'

client = Google::APIClient.new
youtube = client.discovered_api(YOUTUBE_API_SERVICE_NAME, YOUTUBE_API_VERSION)

auth_util = CommandLineOAuthHelper.new(YOUTUBE_READONLY_SCOPE)
client.authorization = auth_util.authorize()

opts = Trollop::options do
  opt :message, 'Required text of message to post.', :type => String
  opt :videoid, 'Optional ID of video to post.', :type => String
  opt :playlistid, 'Optional ID of playlist to post.', :type => String
end

# You can post a message with or without an accompanying video or playlist.
# You can't post both a video and a playlist at the same time.

if opts[:videoid] and opts[:playlistid]
  Trollop::die 'You cannot post a video and a playlist at the same time'
end

Trollop::die :message, 'is required' unless opts[:message]

body = {
  :snippet => {
    :description => opts[:message]
  }
}

if opts[:videoid]
  body[:contentDetails] = {
    :bulletin => {
      :resourceId => {
        :kind => 'youtube#video',
        :videoId => opts[:videoid]
      }
    }
  }
end

if opts[:playlistid]
  body[:contentDetails] = {
    :bulletin => {
      :resourceId => {
        :kind => 'youtube#playlist',
        :playlistId => opts[:playlistid]
      }
    }
  }
end

client.execute!(
  :api_method => youtube.activities.insert,
  :parameters => {
    :part => body.keys.join(',')
  },
  :body_object => body
)
puts 'The bulletin was posted to your channel.'
