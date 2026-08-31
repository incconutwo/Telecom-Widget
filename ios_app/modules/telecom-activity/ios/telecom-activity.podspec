Pod::Spec.new do |s|
  s.name           = 'telecom-activity'
  s.version        = '1.0.0'
  s.summary        = 'Native Telecom Activity & Widget Bridge for iOS'
  s.description    = 'Native Telecom Activity & Widget Bridge for iOS'
  s.author         = 'Telecom Widget'
  s.homepage       = 'https://github.com/incconutwo/Telecom-Widget'
  s.platforms      = { :ios => '16.1' }
  s.source         = { :git => '' }
  s.source_files   = '**/*.{h,m,mm,swift,hpp,cpp}'
  s.dependency 'ExpoModulesCore'
end
