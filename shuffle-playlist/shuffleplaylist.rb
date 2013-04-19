#!/usr/bin/ruby
#  Copyright 2013 Google Inc. All Rights Reserved.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

require 'rubygems'
require 'google/api_client'
require 'json'
require 'logger'
require 'oauth/oauth_util'
require 'thread'
require 'trollop'

YOUTUBE_API_READ_WRITE_SCOPE = 'https://www.googleapis.com/auth/youtube'
YOUTUBE_SERVICE = 'youtube'
YOUTUBE_VERSION = 'v3'

Log = Logger.new(STDOUT)

def initialize_log(debug)
  Log.formatter = proc do |severity, time, progname, msg|
    return "#{time} [#{severity}]: #{msg}\n"
  end

  Log.level = debug ? Logger::DEBUG : Logger::INFO
end

def initialize_api_clients
  client = Google::APIClient.new(:application_name => $0, :application_version => '1.0')
  youtube = client.discovered_api(YOUTUBE_SERVICE, YOUTUBE_VERSION)

  auth_util = CommandLineOAuthHelper.new([YOUTUBE_API_READ_WRITE_SCOPE])
  client.authorization = auth_util.authorize()

  return client, youtube
end

def get_playlist_items(client, youtube, playlist_id)
  playlist_items = []
  next_page_token = ''

  begin
    until next_page_token.nil?
      Log.debug("Fetching #{playlist_id} with page token #{next_page_token}...")
      playlistitems_list_response = client.execute!(
        :api_method => youtube.playlist_items.list,
        :parameters => {
          :playlistId => playlist_id,
          :part => 'snippet',
          :maxResults => 50,
          :pageToken => next_page_token
        }
      )

      playlist_items.concat(playlistitems_list_response.data.items)

      next_page_token = playlistitems_list_response.data.next_page_token
    end
  rescue Google::APIClient::TransmissionError => transmission_error
    Log.error("Error while calling playlistItems.list(): #{transmission_error}")
  ensure
    Log.info("#{playlist_items.length} videos were found in playlist #{playlist_id}.")
    return playlist_items
  end
end

def shuffle_playlist_items(client, youtube, playlist_items)
  playlist_items.shuffle!
  new_position = 0

  begin
    playlist_items.each do |playlist_item|
      Log.info("Moving video #{playlist_item['snippet']['resourceId']['videoId']} from position #{playlist_item['snippet']['position']} to #{new_position}...")
      playlist_item['snippet']['position'] = new_position

      client.execute!(
        :api_method => youtube.playlist_items.update,
        :parameters => {
          :part => 'snippet'
        },
        :body_object => playlist_item
      )

      new_position += 1
    end
  rescue Google::APIClient::TransmissionError => transmission_error
    Log.error("Error while calling playlistItems.update(): #{transmission_error}")
  end
end

if __FILE__ == $PROGRAM_NAME
  opts = Trollop::options do
    opt :playlistId, 'PL-prefixed id of playlist to shuffle', :type => String
    opt :debug, 'Enable for extra logging info'
  end

  initialize_log(opts[:debug])
  Log.info("Starting up.")

  Trollop::die "#{opts[:playlistId]} is not a valid playlist id" unless opts[:playlistId] =~ /^PL/

  client, youtube = initialize_api_clients()

  playlist_items = get_playlist_items(client, youtube, opts[:playlistId])
  shuffle_playlist_items(client, youtube, playlist_items)

  Log.info('All done.')
end