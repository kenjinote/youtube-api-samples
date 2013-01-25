#!/usr/bin/ruby

require 'rubygems'
require 'google/api_client'
# oauth/oauth_util is not part of the official Ruby client library. 
# Get it at http://samples.google-api-ruby-client.googlecode.com/git/oauth/oauth_util.rb
require 'oauth/oauth_util'


# An OAuth 2 access scope that allows for full read/write access.
YOUTUBE_READONLY_SCOPE = 'https://www.googleapis.com/auth/youtube'
YOUTUBE_API_SERVICE_NAME = 'youtube'
YOUTUBE_API_VERSION = 'v3'

client = Google::APIClient.new
youtube = client.discovered_api(YOUTUBE_API_SERVICE_NAME, YOUTUBE_API_VERSION)

auth_util = CommandLineOAuthHelper.new(YOUTUBE_READONLY_SCOPE)
client.authorization = auth_util.authorize()

body = {
  :snippet => {
    :resourceId => {
      :kind => 'youtube#channel',
      # Replace with the id of the channel to subscribe to.
      :channelId => 'UCtVd0c0tGXuTSbU5d8cSBUg'
    }
  }
}

begin
  subscriptions_response = client.execute!(
    :api_method => youtube.subscriptions.insert,
    :parameters => {
      :part => body.keys.join(',')
    },
    :body_object => body
  )
  puts "A subscription to '#{subscriptions_response.data.snippet.title}' was added."
rescue Google::APIClient::ClientError => e
  puts "#{e}: Unable to add subscription. Are you already subscribed?"
end
