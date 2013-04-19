#!/usr/bin/ruby

require 'benchmark'

Benchmark.bm do |x|
  [1, 2, 5, 10, 20].each do |thread_count|
    x.report("#{thread_count} threads:") do
      system('./analyticsdump.rb', '--threads', thread_count.to_s)
    end
    sleep(60)
  end
end