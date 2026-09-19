import { createTheme, alpha } from '@mui/material/styles';

export const createAppTheme = (mode: 'light' | 'dark' = 'light') => {
  const isDark = mode === 'dark';

  return createTheme({
    palette: {
      mode,
      primary: {
        main: isDark ? '#6366F1' : '#4F46E5', // Modern Indigo Sapphire
        light: isDark ? '#818CF8' : '#6366F1',
        dark: isDark ? '#4338CA' : '#3730A3',
        contrastText: '#FFFFFF',
      },
      secondary: {
        main: isDark ? '#38BDF8' : '#0284C7', // Slate Cyan
        light: '#7DD3FC',
        dark: '#0369A1',
        contrastText: '#FFFFFF',
      },
      background: {
        default: isDark ? '#0B0F19' : '#F8FAFC',
        paper: isDark ? '#111827' : '#FFFFFF',
      },
      text: {
        primary: isDark ? '#F8FAFC' : '#0F172A',
        secondary: isDark ? '#94A3B8' : '#64748B',
      },
      divider: isDark ? alpha('#94A3B8', 0.12) : alpha('#0F172A', 0.08),
      success: {
        main: isDark ? '#34D399' : '#059669', // Emerald return
        light: '#6EE7B7',
        dark: '#047857',
        contrastText: '#FFFFFF',
      },
      error: {
        main: isDark ? '#FB7185' : '#E11D48', // Crimson loss
        light: '#FDA4AF',
        dark: '#BE123C',
        contrastText: '#FFFFFF',
      },
      warning: {
        main: isDark ? '#FBBF24' : '#D97706',
        light: '#FDE68A',
        dark: '#B45309',
        contrastText: '#FFFFFF',
      },
      info: {
        main: isDark ? '#38BDF8' : '#0284C7',
        light: '#7DD3FC',
        dark: '#0369A1',
        contrastText: '#FFFFFF',
      },
    },
    typography: {
      fontFamily: [
        'Inter',
        '-apple-system',
        'BlinkMacSystemFont',
        '"Segoe UI"',
        'Roboto',
        '"Helvetica Neue"',
        'Arial',
        'sans-serif',
      ].join(','),
      h1: { fontWeight: 700, letterSpacing: '-0.025em' },
      h2: { fontWeight: 700, letterSpacing: '-0.025em' },
      h3: { fontWeight: 600, letterSpacing: '-0.02em' },
      h4: { fontWeight: 600, letterSpacing: '-0.02em' },
      h5: { fontWeight: 600, letterSpacing: '-0.01em' },
      h6: { fontWeight: 600, letterSpacing: '-0.01em' },
      subtitle1: { fontWeight: 500 },
      subtitle2: { fontWeight: 500 },
      body1: { fontSize: '0.9375rem', lineHeight: 1.6 },
      body2: { fontSize: '0.875rem', lineHeight: 1.5 },
      button: { textTransform: 'none', fontWeight: 600, letterSpacing: '0.01em' },
    },
    shape: {
      borderRadius: 10,
    },
    components: {
      MuiButton: {
        defaultProps: {
          disableElevation: true,
        },
        styleOverrides: {
          root: {
            borderRadius: 8,
            boxShadow: 'none',
            padding: '7px 16px',
            transition: 'all 0.15s ease-in-out',
            '&:hover': {
              boxShadow: 'none',
              transform: 'translateY(-1px)',
            },
            '&:active': {
              transform: 'translateY(0)',
            },
          },
          contained: {
            boxShadow: 'none',
          },
          outlined: {
            borderColor: isDark ? alpha('#94A3B8', 0.25) : alpha('#0F172A', 0.15),
            '&:hover': {
              borderColor: isDark ? '#818CF8' : '#4F46E5',
              backgroundColor: isDark ? alpha('#6366F1', 0.08) : alpha('#4F46E5', 0.04),
            },
          },
        },
      },
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            fontFeatureSettings: "'tnum' 1, 'cv02' 1, 'cv03' 1, 'cv04' 1",
            scrollbarColor: isDark ? '#334155 #0B0F19' : '#CBD5E1 #F8FAFC',
            '&::-webkit-scrollbar, & *::-webkit-scrollbar': {
              width: 8,
              height: 8,
            },
            '&::-webkit-scrollbar-thumb, & *::-webkit-scrollbar-thumb': {
              borderRadius: 8,
              backgroundColor: isDark ? '#334155' : '#CBD5E1',
              minHeight: 24,
              border: `2px solid ${isDark ? '#0B0F19' : '#F8FAFC'}`,
            },
            '&::-webkit-scrollbar-thumb:hover, & *::-webkit-scrollbar-thumb:hover': {
              backgroundColor: isDark ? '#475569' : '#94A3B8',
            },
          },
        },
      },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            backgroundColor: isDark ? '#1E293B' : '#0F172A',
            color: '#FFFFFF',
            fontSize: '0.75rem',
            fontWeight: 500,
            borderRadius: 6,
            padding: '6px 10px',
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.2)',
          },
          arrow: {
            color: isDark ? '#1E293B' : '#0F172A',
          },
        },
      },
      MuiPaper: {
        defaultProps: {
          elevation: 0,
        },
        styleOverrides: {
          root: {
            backgroundImage: 'none',
          },
        },
      },
      MuiCard: {
        defaultProps: {
          elevation: 0,
        },
        styleOverrides: {
          root: {
            borderRadius: 12,
            border: `1px solid ${isDark ? alpha('#94A3B8', 0.12) : alpha('#0F172A', 0.08)}`,
            transition: 'border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease',
            '&:hover': {
              borderColor: isDark ? alpha('#818CF8', 0.3) : alpha('#4F46E5', 0.25),
              boxShadow: isDark
                ? '0 10px 25px -5px rgba(0, 0, 0, 0.4)'
                : '0 10px 25px -5px rgba(15, 23, 42, 0.06)',
            },
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: {
            borderColor: isDark ? alpha('#94A3B8', 0.08) : alpha('#0F172A', 0.06),
            fontFeatureSettings: "'tnum' 1",
          },
          head: {
            fontWeight: 600,
            fontSize: '0.8125rem',
            textTransform: 'uppercase',
            letterSpacing: '0.04em',
            color: isDark ? '#94A3B8' : '#64748B',
            backgroundColor: isDark ? '#1E293B' : '#F8FAFC',
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            fontWeight: 500,
            borderRadius: 6,
            fontFeatureSettings: "'tnum' 1",
          },
          filled: {
            border: `1px solid transparent`,
          },
          outlined: {
            borderColor: isDark ? alpha('#94A3B8', 0.25) : alpha('#0F172A', 0.15),
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: 16,
            border: `1px solid ${isDark ? alpha('#94A3B8', 0.15) : alpha('#0F172A', 0.1)}`,
            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          },
        },
      },
    },
  });
};

export const theme = createAppTheme('light');
