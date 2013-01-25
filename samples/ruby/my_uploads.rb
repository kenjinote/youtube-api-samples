#!/usr/bin/ruby

require 'rubygems'
require 'google/api_client'
# oauth/oauth_util is not part of the official Ruby client library. 
# Get it at http://samples.google-api-ruby-client.googlecode.com/git/oauth/oauth_util.rb
require 'oauth/oauth_util'


# A limited OAuth 2 access scope that allows for read-only access.
YOUTUBE_READONLY_SCOPE = 'https://www.googleapis.com/auth/youtube.readonly'
YOUTUBE_API_SERVICE_NAME = 'youtube'
YOUTUBE_API_VERSION = 'v3'

client = Google::APIClient.new
youtube = client.discovered_api(YOUTUBE_API_SERVICE_NAME, YOUTUBE_API_VERSION)

auth_util = CommandLineOAuthHelper.new(YOUTUBE_READONLY_SCOPE)
client.authorization = auth_util.authorize()

channels_response = client.execute!(
  :api_method => youtube.channels.list,
  :parameters => {
    :mine => '',
    :part => 'contentDetails'
  }
)

channels_response.data.items.each do |channel|
  uploads_list_id = channel['contentDetails']['uploads']

  playlistitems_response = client.execute!(
    :api_method => youtube.playlist_items.list,
    :parameters => {
      :playlistId => uploads_list_id,
      :part => 'snippet',
      :maxResults => 50
    }
  )

  puts "Videos in list #{uploads_list_id}"

  playlistitems_response.data.items.each do |playlist_item|
    title = playlist_item['snippet']['title']
    video_id = playlist_item['snippet']['resourceId']['videoId']

    puts "#{title} (#{video_id})"
  end

  puts
end
