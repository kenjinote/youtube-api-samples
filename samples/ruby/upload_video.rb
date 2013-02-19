NOT READY YET

#!/usr/bin/ruby

require 'rubygems'
require 'google/api_client'
require 'oauth/oauth_util'

client = Google::APIClient.new
youtube = client.discovered_api('youtube', 'v3alpha')

auth_util = CommandLineOAuthHelper.new('https://www.googleapis.com/auth/youtube')
client.authorization = auth_util.authorize()

videos_response = client.execute!(
  :api_method => youtube.videos.insert,
  :body_object => {
    :snippet => {
      :title => 'Test Title',
      :description => 'Test Description',
      :categoryId => 'VIDEO_CATEGORY_PEOPLE'
    }
  },
  :media => Google::APIClient::UploadIO.new('file.mov', 'video/quicktime'),
  :parameters => {
    'part' => 'snippet',
    'uploadType' => 'resumable',
    'alt' => 'json'
  }
)